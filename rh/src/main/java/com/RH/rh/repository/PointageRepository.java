package com.RH.rh.repository;

import com.RH.rh.model.Pointage;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lecture des pointages.
 *
 * Toutes les tables (pointages, agents, equipes, membres_equipe,
 * affectations) sont lues dans SQL Server. Les pointages arrivent dans
 * SQL Server via {@link com.RH.rh.service.PointageSyncService}, qui les
 * recopie depuis Turso avant de les supprimer de Turso.
 *
 * Le JdbcTemplate injecté est le JdbcTemplate @Primary (SQL Server).
 *
 * IMPORTANT : la colonne pointages.date_heure est de type DATETIME
 * dans SQL Server (et non NVARCHAR). Les filtres de date utilisent
 * donc CAST(... AS DATE) plutôt qu'une manipulation de texte
 * (LEFT/REPLACE), qui n'était pas fiable sur une colonne DATETIME.
 */
@Repository
public class PointageRepository {

    private final JdbcTemplate jdbcTemplate;

    public PointageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Retrouve le nom de l'équipe d'un agent à partir de son id.
     *
     * Relation :
     * membres_equipe.agent_id -> agents.id
     */
    public String findEquipeParAgentId(Long agentId) {

        try {

            return jdbcTemplate.queryForObject(
                """
                SELECT TOP 1 eq.nom
                FROM membres_equipe me
                JOIN equipes eq
                    ON eq.id = me.equipe_id
                WHERE me.agent_id = ?
                """,
                String.class,
                agentId
            );

        } catch (EmptyResultDataAccessException ex) {

            return null;
        }
    }

    /**
     * Chefs d'équipe (role_equipe = 'CHEF'), indépendamment des
     * pointages du jour — utilisé pour l'affichage "dirigée par" côté
     * vue, sans dépendre de qui a effectivement pointé.
     *
     * @return une Map équipe -> liste des noms des chefs de cette équipe
     */
    public Map<String, List<String>> findChefsParEquipe() {

        List<Map<String, Object>> lignes = jdbcTemplate.queryForList(
            """
            SELECT eq.nom AS equipe, CONCAT(ag.nom, ' ', ag.prenom) AS chef
            FROM membres_equipe me
            JOIN equipes eq ON eq.id = me.equipe_id
            JOIN agents ag ON ag.id = me.agent_id
            WHERE me.role_equipe = 'CHEF'
            """
        );

        Map<String, List<String>> resultat = new LinkedHashMap<>();

        for (Map<String, Object> ligne : lignes) {

            String equipe = (String) ligne.get("equipe");
            String chef = (String) ligne.get("chef");

            resultat.computeIfAbsent(equipe, k -> new ArrayList<>()).add(chef);
        }

        return resultat;
    }

    /**
     * Recherche les pointages.
     *
     * L'affectation affichée correspond uniquement à l'affectation de
     * l'agent pour la DATE DU POINTAGE.
     */
    public List<Pointage> rechercher(
            String dateDebut,
            String dateFin,
            String telephone,
            String equipe,
            boolean isAdmin,
            String equipeUtilisateur) {

        // Sécurité serveur :
        // un non-admin ne peut pas choisir une autre équipe.
        String equipeEffective =
                isAdmin ? equipe : equipeUtilisateur;

        boolean filtreTelephone =
                telephone != null && !telephone.isBlank();

        boolean filtreEquipe =
                equipeEffective != null && !equipeEffective.isBlank();

        List<Object> params = new ArrayList<>();

        StringBuilder sql = new StringBuilder();

        sql.append("""
            SELECT DISTINCT

                p.telephone AS telephone,

                CONCAT(ag.matricule, ' : ', ag.nom, ' ', ag.prenom) AS agent,

                COALESCE(eq.nom, N'Sans équipe') AS equipe,

                affect.sites AS lieu_affectation,

                p.lieu AS site,

                CASE
                    WHEN affect.sites IS NULL
                        THEN 'intrus'
                    WHEN CHARINDEX(
                            CONCAT(',', p.lieu, ','),
                            CONCAT(',', affect.sites, ',')
                         ) > 0
                        THEN 'ok'
                    ELSE 'intrus'
                END AS type_lieu,

                p.date_heure AS date_heure,

                p.type AS type,

                ag.nom AS nom_agent,

                me.role_equipe AS role_equipe

            FROM pointages AS p

            INNER JOIN agents AS ag
                ON ag.telephone = p.telephone

            LEFT JOIN membres_equipe AS me
                ON me.agent_id = ag.id

            LEFT JOIN equipes AS eq
                ON eq.id = me.equipe_id

            -- affectation exactement à la date du pointage
            LEFT JOIN affectations AS affect
                ON affect.agent_id = ag.id
                AND CAST(p.date_heure AS DATE) = affect.date_affectation

            WHERE CAST(p.date_heure AS DATE) BETWEEN CAST(? AS DATE) AND CAST(? AS DATE)
            """);

        params.add(dateDebut);
        params.add(dateFin);

        if (filtreTelephone) {
            sql.append(" AND p.telephone = ? ");
            params.add(telephone);
        }

        if (filtreEquipe) {
            sql.append(" AND eq.nom = ? ");
            params.add(equipeEffective);
        }

        sql.append("""

            ORDER BY
                equipe ASC,
                date_heure ASC
            """);

        return jdbcTemplate.query(

            sql.toString(),

            (rs, rowNum) -> {

                Pointage p = new Pointage();

                p.setTelephone(rs.getString("telephone"));
                p.setAgent(rs.getString("agent"));
                p.setEquipe(rs.getString("equipe"));
                p.setLieuAffectation(rs.getString("lieu_affectation"));
                p.setSite(rs.getString("site"));
                p.setTypeLieu(rs.getString("type_lieu"));
                p.setNomAgent(rs.getString("nom_agent"));
                p.setRoleEquipe(rs.getString("role_equipe"));

                String dateHeure = rs.getString("date_heure");

                if (dateHeure != null && dateHeure.length() >= 19) {

                    p.setDate(dateHeure.substring(0, 10));
                    p.setHeure(dateHeure.substring(11, 19));

                } else {

                    p.setDate("");
                    p.setHeure("");
                }

                p.setType(rs.getString("type"));

                return p;
            },

            params.toArray()
        );
    }

    /**
     * Recherche les pointages du jour.
     */
    public List<Pointage> rechercherDuJour(
            String date,
            String telephone,
            String equipe,
            boolean isAdmin,
            String equipeUtilisateur) {

        return rechercher(
            date,
            date,
            telephone,
            equipe,
            isAdmin,
            equipeUtilisateur
        );
    }

    /**
     * Liste des équipes.
     */
    public List<String> findEquipes() {

        return jdbcTemplate.queryForList(
            "SELECT nom FROM equipes ORDER BY nom",
            String.class
        );
    }
}