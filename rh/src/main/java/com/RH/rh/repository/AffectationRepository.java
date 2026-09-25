package com.RH.rh.repository;

import com.RH.rh.model.AffectationForm;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class AffectationRepository {

    private final JdbcTemplate jdbcTemplate;

    public AffectationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void update(AffectationForm affectation) {

        jdbcTemplate.execute(

            """
            UPDATE affectations
            SET sites = ?,
            date_affectation = ?,
            agent_id = ?
            WHERE id = ?
            """,

            (PreparedStatement ps) -> {

                ps.setString(1, affectation.getSites());
                ps.setString(2, affectation.getDateAffectation().toString());
                ps.setLong(3, affectation.getAgentId());
                ps.setLong(4, affectation.getId());

                return ps.execute();
            }
        );
    }
    /**
     * Retourne toutes les affectations.
     */
    public List<Map<String, Object>> findAll() {

        String sql = """
                SELECT
                    a.id,
                    a.date_affectation,
                    ag.id AS agent_id,
                    ag.matricule,
                    CONCAT(ag.nom, ' ', ag.prenom) AS agent,
                    a.sites
                FROM affectations a
                INNER JOIN agents ag
                    ON ag.id = a.agent_id
                ORDER BY
                    a.date_affectation DESC,
                    ag.nom,
                    ag.prenom
                """;

        return jdbcTemplate.queryForList(sql);
    }

    /**
     * Recherche uniquement par texte.
     *
     * Recherche dans :
     * - nom
     * - prénom
     * - matricule
     * - sites
     */
    public List<Map<String, Object>> search(String query) {

        String sql = """
                SELECT
                    a.id,
                    a.date_affectation,
                    ag.id AS agent_id,
                    ag.matricule,
                    CONCAT(ag.nom, ' ', ag.prenom) AS agent,
                    a.sites
                FROM affectations a
                INNER JOIN agents ag
                    ON ag.id = a.agent_id
                WHERE
                    LOWER(ag.nom) LIKE LOWER(?)
                    OR LOWER(ag.prenom) LIKE LOWER(?)
                    OR LOWER(ag.matricule) LIKE LOWER(?)
                    OR LOWER(a.sites) LIKE LOWER(?)
                ORDER BY
                    a.date_affectation DESC,
                    ag.nom,
                    ag.prenom
                """;

        String like = "%" + query.trim() + "%";

        return jdbcTemplate.queryForList(
                sql,
                like,
                like,
                like,
                like
        );
    }
    

    /**
     * Recherche combinée :
     *
     * - texte
     * - date début
     * - date fin
     *
     * Tous les paramètres sont facultatifs.
     */
    public List<Map<String, Object>> search(
            String query,
            LocalDate debut,
            LocalDate fin
    ) {

        StringBuilder sql = new StringBuilder("""
                SELECT
                    a.id,
                    a.date_affectation,
                    ag.id AS agent_id,
                    ag.matricule,
                    CONCAT(ag.nom, ' ', ag.prenom) AS agent,
                    a.sites
                FROM affectations a
                INNER JOIN agents ag
                    ON ag.id = a.agent_id
                WHERE 1 = 1
                """);

        List<Object> params = new ArrayList<>();

        /*
         * Recherche texte
         */
        if (query != null && !query.isBlank()) {

            sql.append("""
                    AND (
                        LOWER(ag.nom) LIKE LOWER(?)
                        OR LOWER(ag.prenom) LIKE LOWER(?)
                        OR LOWER(ag.matricule) LIKE LOWER(?)
                        OR LOWER(a.sites) LIKE LOWER(?)
                    )
                    """);

            String like = "%" + query.trim() + "%";

            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }

        /*
         * Date de début
         */
        if (debut != null) {

            sql.append("""
                    AND a.date_affectation >= ?
                    """);

            params.add(debut.toString());
        }

        /*
         * Date de fin
         */
        if (fin != null) {

            sql.append("""
                    AND a.date_affectation <= ?
                    """);

            params.add(fin.toString());
        }

        /*
         * Tri
         */
        sql.append("""
                ORDER BY
                    a.date_affectation DESC,
                    ag.nom,
                    ag.prenom
                """);

        return jdbcTemplate.queryForList(
                sql.toString(),
                params.toArray()
        );
    }

    public Map<String, Object> findById(Long id) {

        String sql = """
                SELECT
                    a.id,
                    a.agent_id,
                    a.date_affectation,
                    a.sites,
                    ag.matricule,
                    ag.nom,
                    ag.prenom
                FROM affectations a
                INNER JOIN agents ag
                    ON ag.id = a.agent_id
                WHERE a.id = ?
                """;

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(sql, id);

        if (results.isEmpty()) {
            return null;
        }

        return results.get(0);
    }

    /**
     * Enregistre une affectation.
     */
    public void save(AffectationForm form) {

        String sitesAsText = String.join(", ", form.getSites());

        String sql = """
                INSERT INTO affectations
                (
                    agent_id,
                    date_affectation,
                    sites
                )
                VALUES (?, ?, ?)
                """;

        jdbcTemplate.execute((Connection con) -> {
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setLong(1, form.getAgentId());
                ps.setString(2, form.getDateAffectation().toString());
                ps.setString(3, sitesAsText);
                ps.execute();          // <-- execute() au lieu de executeUpdate()
            }
            return null;
        });
    }

    /**
     * Retourne les affectations d'une date précise.
     */
    public List<Map<String, Object>> findByDate(
            LocalDate date
    ) {

        String sql = """
                SELECT
                    a.id,
                    a.date_affectation,
                    ag.id AS agent_id,
                    ag.matricule,
                    CONCAT(ag.nom, ' ', ag.prenom) AS agent,
                    a.sites
                FROM affectations a
                INNER JOIN agents ag
                    ON ag.id = a.agent_id
                WHERE a.date_affectation = ?
                ORDER BY
                    ag.nom,
                    ag.prenom
                """;

        return jdbcTemplate.queryForList(
                sql,
                date.toString()
        );
    }

    /**
     * Retourne les affectations comprises entre deux dates.
     *
     * Les dates debut et fin sont incluses.
     */
    public List<Map<String, Object>> findBetween(
            LocalDate debut,
            LocalDate fin
    ) {

        String sql = """
                SELECT
                    a.id,
                    a.date_affectation,
                    ag.id AS agent_id,
                    ag.matricule,
                    CONCAT(ag.nom, ' ', ag.prenom) AS agent,
                    a.sites
                FROM affectations a
                INNER JOIN agents ag
                    ON ag.id = a.agent_id
                WHERE a.date_affectation >= ?
                  AND a.date_affectation <= ?
                ORDER BY
                    a.date_affectation DESC,
                    ag.nom,
                    ag.prenom
                """;

        return jdbcTemplate.queryForList(
                sql,
                debut.toString(),
                fin.toString()
        );
    }

    /**
     * Supprime une affectation.
     */
    public void delete(Long id) {

        String sql = """
                DELETE FROM affectations
                WHERE id = ?
                """;

        jdbcTemplate.execute((Connection con) -> {

            try (PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setLong(1, id);

                // Utiliser execute() car le driver ne supporte pas executeUpdate()
                ps.execute();
            }

            return null;
        });
    }
}