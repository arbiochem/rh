package com.RH.rh.repository;

import com.RH.rh.model.Pointage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PointageRepository {

    private final JdbcTemplate jdbcTemplate;

    public PointageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Pointage> rechercher(String dateDebut, String dateFin,
                                     String telephone, String equipe) {

        String sql = """
            SELECT DISTINCT
                p.telephone AS telephone,
                ag.matricule || ' : ' || ag.nom || ' ' || ag.prenom AS agent,
                COALESCE(eq.nom, 'Sans équipe') AS equipe,
                affect.sites AS "lieu_affectation",
                p.lieu AS site,
                CASE
                    WHEN affect.sites IS NULL THEN 'intrus'
                    WHEN ',' || affect.sites || ',' LIKE '%,' || p.lieu || ',%' THEN 'ok'
                    ELSE 'intrus'
                END AS type_lieu,
                p.date_heure,
                p.type,
                ag.nom AS nom_agent,
                me.role_equipe
            FROM pointages AS p
            INNER JOIN agents AS ag
                ON ag.telephone = p.telephone
            LEFT JOIN membres_equipe AS me
                ON me.utilisateur_id = ag.id
            LEFT JOIN equipes AS eq
                ON eq.id = me.equipe_id
            LEFT JOIN affectations AS affect
                ON affect.agent_id = ag.id
            WHERE replace(substr(p.date_heure, 1, 10),'/','-') BETWEEN ? AND ?
            AND (? IS NULL OR ? = '' OR p.telephone = ?)
            AND (? IS NULL OR ? = '' OR eq.nom = ?)
            ORDER BY equipe ASC, p.date_heure ASC
            """;

        return jdbcTemplate.query(
            sql,
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
            dateDebut,
            dateFin,
            telephone, telephone, telephone,
            equipe, equipe, equipe
        );
    }

    public List<Pointage> rechercherDuJour(String date, String telephone,
                                           String equipe) {
        return rechercher(date, date, telephone, equipe);
    }

    // Liste des équipes pour le menu déroulant
    public List<String> findEquipes() {
        return jdbcTemplate.queryForList(
            "SELECT nom FROM equipes ORDER BY nom",
            String.class
        );
    }
}