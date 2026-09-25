package com.RH.rh.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Synchronisation de la table "pointages" : Turso -> SQL Server.
 *
 * Les pointages sont saisis dans Turso. Ce service :
 *
 *   1. lit un lot de pointages dans Turso ;
 *   2. les insère dans SQL Server (transaction, insertion idempotente) ;
 *   3. UNIQUEMENT si l'insertion a été validée (commit), supprime dans
 *      Turso exactement les lignes qui viennent d'être copiées.
 *
 * Un pointage n'est donc jamais supprimé de Turso avant d'être sûr
 * dans SQL Server, et un pointage arrivé dans Turso pendant la
 * synchronisation reste en place jusqu'à la synchronisation suivante.
 *
 * Si Turso ou SQL Server est injoignable, l'erreur est journalisée et
 * rien n'est supprimé : la synchronisation reprendra la fois suivante.
 *
 * Les lignes dont la date_heure est illisible (format inattendu) sont
 * ignorées pour SQL Server mais restent dans Turso (elles ne sont pas
 * supprimées), afin de ne pas perdre de données et de pouvoir les
 * corriger/rejouer plus tard.
 */
@Service
public class PointageSyncService {

    private static final Logger log =
            LoggerFactory.getLogger(PointageSyncService.class);

    /** Nombre de pointages lus / copiés / supprimés par lot. */
    private static final int TAILLE_LOT = 200;

    /** Garde-fou : nombre maximal de lots par synchronisation. */
    private static final int MAX_LOTS = 500;

    private static final String SELECT_TURSO = """
            SELECT rowid AS rid, telephone, lieu, date_heure, type
            FROM pointages
            ORDER BY rowid
            LIMIT %d
            """.formatted(TAILLE_LOT);

    /**
     * Insertion idempotente : si le même pointage existe déjà dans
     * SQL Server (cas d'un précédent lot copié mais non supprimé de
     * Turso), il n'est pas dupliqué.
     */
    private static final String INSERT_SQLSERVER = """
            INSERT INTO pointages (telephone, lieu, date_heure, type)
            SELECT ?, ?, ?, ?
            WHERE NOT EXISTS (
                SELECT 1
                FROM pointages t
                WHERE t.telephone = ?
                  AND t.date_heure = ?
                  AND ISNULL(t.lieu, '') = ISNULL(?, '')
                  AND ISNULL(t.type, '') = ISNULL(?, '')
            )
            """;

    /**
     * Formats acceptés pour date_heure tel que stocké dans Turso.
     * Ajoute ici d'autres formats si la pointeuse en produit d'autres.
     */
    private static final List<DateTimeFormatter> FORMATS_DATE = List.of(
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.FRENCH),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.FRENCH),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss", Locale.FRENCH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.FRENCH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.FRENCH)
    );
    

    private final JdbcTemplate sqlServer;
    private final JdbcTemplate turso;
    private final TransactionTemplate sqlServerTx;

    private final ReentrantLock verrou = new ReentrantLock();
    private volatile boolean tableVerifiee = false;

    public PointageSyncService(
            @Qualifier("sqlServerJdbcTemplate") JdbcTemplate sqlServer,
            @Qualifier("tursoJdbcTemplate") JdbcTemplate turso,
            @Qualifier("sqlServerDataSource") DataSource sqlServerDataSource) {

        this.sqlServer = sqlServer;
        this.turso = turso;
        this.sqlServerTx = new TransactionTemplate(
                new DataSourceTransactionManager(sqlServerDataSource));
    }

    /**
     * Synchronisation périodique (désactivée par défaut).
     * Activer avec par exemple : app.pointage.sync.cron=0 * * * * *
     */
    @Scheduled(cron = "${app.pointage.sync.cron:-}")
    public void synchroniserPlanifie() {
        synchroniser();
    }

    /**
     * Résultat d'une synchronisation.
     *
     * @param transferes nombre de pointages transférés
     * @param enCours    true si une autre synchronisation était déjà en cours
     * @param erreur     message d'erreur, ou null si tout s'est bien passé
     */
    public record ResultatSync(int transferes, boolean enCours, String erreur) {
        public boolean ok() {
            return !enCours && erreur == null;
        }
    }

    /**
     * Synchronisation automatique (avant chaque recherche) : ne lève jamais
     * d'exception, la recherche continue avec les données déjà présentes
     * dans SQL Server.
     *
     * @return le nombre de pointages transférés
     */
    public int synchroniser() {
        return synchroniserAvecResultat().transferes();
    }

    /**
     * Copie les pointages de Turso vers SQL Server puis les supprime de
     * Turso, et renvoie le détail (utilisé par le bouton « Actualiser »).
     * Ne lève jamais d'exception : en cas d'échec, l'erreur est journalisée
     * et renvoyée dans le résultat.
     */
    public ResultatSync synchroniserAvecResultat() {

        // Une seule synchronisation à la fois ; si une autre est en
        // cours, on n'attend pas.
        if (!verrou.tryLock()) {
            return new ResultatSync(0, true, null);
        }

        int total = 0;

        try {

            verifierTableSqlServer();

            for (int lot = 0; lot < MAX_LOTS; lot++) {

                List<LignePointage> lignes = lireLotTurso();

                if (lignes.isEmpty()) {
                    break;
                }

                // 1) copie dans SQL Server (transaction validée ici).
                //    Seules les lignes à date valide sont copiées ;
                //    "copiees" contient exactement ce qui a été inséré.
                List<LignePointage> copiees = copierDansSqlServer(lignes);

                int ignorees = lignes.size() - copiees.size();
                if (ignorees > 0) {
                    log.warn("{} ligne(s) ignorée(s) (date_heure illisible), "
                            + "conservée(s) dans Turso pour correction", ignorees);
                }

                // 2) suppression dans Turso, seulement des lignes
                //    réellement copiées dans SQL Server
                if (!copiees.isEmpty()) {
                    supprimerDansTurso(copiees);
                }

                total += copiees.size();

                if (lignes.size() < TAILLE_LOT) {
                    break;
                }
            }

            if (total > 0) {
                log.info("Synchronisation pointages : {} ligne(s) transférée(s) de Turso vers SQL Server", total);
            }

            return new ResultatSync(total, false, null);

        } catch (Exception e) {

            log.error("Synchronisation des pointages interrompue "
                    + "(rien n'est supprimé de Turso tant que la copie "
                    + "n'est pas validée) : {}", e.toString());

            return new ResultatSync(total, false, e.toString());

        } finally {

            verrou.unlock();
        }
    }

    // ---------------------------------------------------------------
    // SQL Server : création de la table si nécessaire
    // ---------------------------------------------------------------

    private void verifierTableSqlServer() throws Exception {

        if (tableVerifiee) {
            return;
        }

        String ddl = new String(
                new ClassPathResource("db/sqlserver_pointages.sql")
                        .getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        sqlServer.execute(ddl);

        tableVerifiee = true;
    }

    // ---------------------------------------------------------------
    // Turso : lecture d'un lot
    // ---------------------------------------------------------------

    private List<LignePointage> lireLotTurso() {

        return turso.query(SELECT_TURSO, (rs, i) -> new LignePointage(
                rs.getLong("rid"),
                nvl(rs.getString("telephone")),
                rs.getString("lieu"),
                nvl(rs.getString("date_heure")),
                rs.getString("type")
        ));
    }

    // ---------------------------------------------------------------
    // SQL Server : copie transactionnelle d'un lot
    // ---------------------------------------------------------------

    /**
     * Copie dans SQL Server les lignes du lot dont la date_heure est
     * interprétable, et renvoie la liste de celles effectivement
     * copiées (sous-ensemble de {@code lignes}).
     *
     * Les lignes à date illisible sont exclues de l'insertion ET du
     * retour : elles ne seront donc pas supprimées de Turso, et
     * resteront disponibles pour être corrigées ou diagnostiquées.
     */
    private List<LignePointage> copierDansSqlServer(List<LignePointage> lignes) {

        List<LignePointage> valides = new ArrayList<>();

        for (LignePointage l : lignes) {
            if (parseDateHeure(l.dateHeure()) != null) {
                valides.add(l);
            } else {
                log.warn("date_heure illisible, ligne ignorée (telephone={}, rowid={}) : '{}'",
                        l.telephone(), l.rowid(), l.dateHeure());
            }
        }

        if (valides.isEmpty()) {
            return valides;
        }

        sqlServerTx.executeWithoutResult(status ->
                sqlServer.batchUpdate(INSERT_SQLSERVER, valides, valides.size(),
                        (ps, l) -> {

                            Timestamp ts = parseDateHeure(l.dateHeure());

                            ps.setString(1, l.telephone());
                            ps.setString(2, l.lieu());
                            ps.setTimestamp(3, ts);
                            ps.setString(4, l.type());
                            ps.setString(5, l.telephone());
                            ps.setTimestamp(6, ts);
                            ps.setString(7, l.lieu());
                            ps.setString(8, l.type());
                        }));

        return valides;
    }

    /**
     * Parse une date_heure Turso (texte) en Timestamp SQL Server, en
     * essayant successivement les formats de {@link #FORMATS_DATE}.
     *
     * @return le Timestamp correspondant, ou null si la valeur est
     *         vide ou ne correspond à aucun format connu.
     */
    private static Timestamp parseDateHeure(String brut) {

        if (brut == null || brut.isBlank()) {
            return null;
        }

        String valeur = brut.trim();

        for (DateTimeFormatter fmt : FORMATS_DATE) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(valeur, fmt);
                return Timestamp.valueOf(ldt);
            } catch (DateTimeParseException ignore) {
                // essaie le format suivant
            }
        }

        return null;
    }

    // ---------------------------------------------------------------
    // Turso : suppression des lignes copiées (et d'elles seules)
    // ---------------------------------------------------------------

    private void supprimerDansTurso(List<LignePointage> lignes) {

        List<Long> ids = new ArrayList<>(lignes.size());
        for (LignePointage l : lignes) {
            ids.add(l.rowid());
        }

        // Le pilote libSQL ne supporte pas executeUpdate() :
        // on passe par execute(), comme dans les autres repositories.
        final int paquet = 50;

        for (int debut = 0; debut < ids.size(); debut += paquet) {

            List<Long> sousListe =
                    ids.subList(debut, Math.min(debut + paquet, ids.size()));

            String marqueurs = String.join(",",
                    java.util.Collections.nCopies(sousListe.size(), "?"));

            String sql = "DELETE FROM pointages WHERE rowid IN ("
                    + marqueurs + ")";

            turso.execute((Connection con) -> {

                try (PreparedStatement ps = con.prepareStatement(sql)) {

                    for (int i = 0; i < sousListe.size(); i++) {
                        ps.setLong(i + 1, sousListe.get(i));
                    }

                    ps.execute();
                }

                return null;
            });
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private record LignePointage(
            long rowid,
            String telephone,
            String lieu,
            String dateHeure,
            String type) {
    }
}