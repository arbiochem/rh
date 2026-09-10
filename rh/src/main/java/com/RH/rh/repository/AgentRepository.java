package com.RH.rh.repository;

import com.RH.rh.model.Agent;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class AgentRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    public List<Agent> findAll() {

        return jdbcTemplate.query(
            "SELECT id, matricule, nom, prenom, telephone, email, actif FROM agents",
            (rs, rowNum) -> {

                Agent agent = new Agent();

                agent.setId(rs.getLong("id"));
                agent.setMatricule(rs.getString("matricule"));
                agent.setNom(rs.getString("nom"));
                agent.setPrenom(rs.getString("prenom"));
                agent.setTelephone(rs.getString("telephone"));
                agent.setEmail(rs.getString("email"));

                return agent;
            }
        );
    }


    public void save(Agent agent) {

        jdbcTemplate.execute(

            """
            INSERT INTO agents
                (matricule, nom, prenom, telephone, email, actif)
            VALUES (?, ?, ?, ?, ?, ?)
            """,

            (PreparedStatement ps) -> {

                ps.setString(1, agent.getMatricule());
                ps.setString(2, agent.getNom());
                ps.setString(3, agent.getPrenom());
                ps.setString(4, agent.getTelephone());
                ps.setString(5, agent.getEmail());
                ps.setBoolean(6, true);

                return ps.execute();
            }
        );
    }

    public Agent findById(Long id) {

        return jdbcTemplate.queryForObject(
            "SELECT id, matricule, nom, prenom, telephone, email, actif FROM agents WHERE id = ?",
            (rs, rowNum) -> {

                Agent agent = new Agent();

                agent.setId(rs.getLong("id"));
                agent.setMatricule(rs.getString("matricule"));
                agent.setNom(rs.getString("nom"));
                agent.setPrenom(rs.getString("prenom"));
                agent.setTelephone(rs.getString("telephone"));
                agent.setEmail(rs.getString("email"));

                return agent;
            },
            id
        );
    }


    public void update(Agent agent) {

        jdbcTemplate.execute(

            """
            UPDATE agents
            SET matricule = ?, nom = ?, prenom = ?, telephone = ?, email = ?
            WHERE id = ?
            """,

            (PreparedStatement ps) -> {

                ps.setString(1, agent.getMatricule());
                ps.setString(2, agent.getNom());
                ps.setString(3, agent.getPrenom());
                ps.setString(4, agent.getTelephone());
                ps.setString(5, agent.getEmail());
                ps.setLong(6, agent.getId());

                return ps.execute(); // execute(), pas executeUpdate() — driver libSQL
            }
        );
    }

    public List<Map<String, Object>> search(
            String query,
            String prenom,
            String nom,
            String telephone
    ) {

        StringBuilder sql = new StringBuilder("""
                SELECT
                    id,
                    matricule,
                    nom,
                    prenom,
                    telephone,
                    email
                FROM agents 
                WHERE 1 = 1
                """);

        List<Object> params = new ArrayList<>();

        /*
         * Recherche texte
         */
        if (query != null && !query.isBlank()) {

            sql.append("""
                    AND (
                        LOWER(nom) LIKE LOWER(?)
                        OR LOWER(prenom) LIKE LOWER(?)
                        OR LOWER(matricule) LIKE LOWER(?)
                        OR LOWER(telephone) LIKE LOWER(?)
                    )
                    """);

            String like = "%" + query.trim() + "%";

            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }

        
        sql.append("""
                ORDER BY
                    id ASC,
                    nom,
                    prenom
                """);

        return jdbcTemplate.queryForList(
                sql.toString(),
                params.toArray()
        );
    }

    public List<Map<String, Object>> search(String query) {
        String sql = """
                SELECT
                    id,
                    matricule,
                    nom,
                    prenom,
                    telephone,
                    email
                FROM agents
                WHERE
                    LOWER(nom) LIKE LOWER(?)
                    OR LOWER(prenom) LIKE LOWER(?)
                    OR LOWER(matricule) LIKE LOWER(?)
                    OR LOWER(telephone) LIKE LOWER(?)
                ORDER BY
                    id ASC,
                    nom,
                    prenom
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

    public void delete(Long id) {

        jdbcTemplate.execute(

            "DELETE FROM agents WHERE id = ?",

            (PreparedStatement ps) -> {

                ps.setLong(1, id);

                return ps.execute();
            }
        );
    }
}