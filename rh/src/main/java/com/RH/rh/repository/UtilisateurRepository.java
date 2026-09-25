package com.RH.rh.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import com.RH.rh.model.Utilisateur;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class UtilisateurRepository {

    private final JdbcTemplate jdbc;

    public UtilisateurRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Utilisateur mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Utilisateur u = new Utilisateur();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setRole(rs.getString("role"));
        u.setActif(rs.getInt("actif") == 1);

        long agentId = rs.getLong("agent_id");
        if (!rs.wasNull()) {
            u.setAgentId(agentId);
        }

        try {
            u.setAgentNom(rs.getString("agent_nom"));
            u.setAgentPrenom(rs.getString("agent_prenom"));
        } catch (java.sql.SQLException ignored) {
        }

        return u;
    }

    /** Conservé pour compatibilité avec UtilisateurDetailsServiceImpl. */
    public Map<String, Object> findByUsername(String username) {
        return jdbc.queryForMap("""
            SELECT id, username, password, role, actif, created_at
            FROM utilisateurs
            WHERE username = ?
        """, username);
    }

    /** Retourne l'agent_id lié au compte utilisateur (vide si aucun agent lié ou utilisateur introuvable). */
    public Optional<Long> findAgentIdByUsername(String username) {
        try {
            Long agentId = jdbc.queryForObject(
                "SELECT agent_id FROM utilisateurs WHERE username = ?",
                Long.class, username
            );
            return Optional.ofNullable(agentId);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<Utilisateur> findAll() {
        String sql = """
            SELECT u.id, u.username, u.password, u.role, u.actif, u.agent_id,
                   a.nom AS agent_nom, a.prenom AS agent_prenom
            FROM utilisateurs u
            LEFT JOIN agents a ON a.id = u.agent_id
            ORDER BY u.username
            """;
        return jdbc.query(sql, this::mapRow);
    }

    public Optional<Utilisateur> findById(Long id) {
        String sql = """
            SELECT u.id, u.username, u.password, u.role, u.actif, u.agent_id,
                   a.nom AS agent_nom, a.prenom AS agent_prenom
            FROM utilisateurs u
            LEFT JOIN agents a ON a.id = u.agent_id
            WHERE u.id = ?
            """;
        try {
            Utilisateur u = jdbc.queryForObject(sql, this::mapRow, id);
            return Optional.ofNullable(u);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public boolean existsByUsername(String username) {
        String sql = "SELECT COUNT(*) FROM utilisateurs WHERE username = ?";
        Integer count = jdbc.queryForObject(sql, Integer.class, username);
        return count != null && count > 0;
    }

    public boolean agentDejaLie(Long agentId) {
        String sql = "SELECT COUNT(*) FROM utilisateurs WHERE agent_id = ?";
        Integer count = jdbc.queryForObject(sql, Integer.class, agentId);
        return count != null && count > 0;
    }

    public Utilisateur save(Utilisateur utilisateur) {

        // SQL Server : OUTPUT INSERTED.id renvoie l'id généré dans la même requête.
        Long newId = jdbc.query(
            """
            INSERT INTO utilisateurs (username, password, role, actif, agent_id, created_at)
            OUTPUT INSERTED.id
            VALUES (?, ?, ?, 1, ?, ?)
            """,
            (PreparedStatement ps) -> {
                ps.setString(1, utilisateur.getUsername());
                ps.setString(2, utilisateur.getPassword());
                ps.setString(3, utilisateur.getRole());
                if (utilisateur.getAgentId() != null) {
                    ps.setLong(4, utilisateur.getAgentId());
                } else {
                    ps.setNull(4, java.sql.Types.BIGINT);
                }
                ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            },
            (ResultSetExtractor<Long>) rs -> {
                rs.next();
                return rs.getLong(1);
            }
        );

        utilisateur.setId(newId);
        utilisateur.setActif(true);
        return utilisateur;
    }

    public int update(Long id, String role) {
        jdbc.execute(
            "UPDATE utilisateurs SET role = ? WHERE id = ?",
            (PreparedStatement ps) -> {
                ps.setString(1, role);
                ps.setLong(2, id);
                return ps.execute();
            }
        );
        return 1;
    }

    public int changerMotDePasse(Long id, String nouveauPasswordHash) {
        jdbc.execute(
            "UPDATE utilisateurs SET password = ? WHERE id = ?",
            (PreparedStatement ps) -> {
                ps.setString(1, nouveauPasswordHash);
                ps.setLong(2, id);
                return ps.execute();
            }
        );
        return 1;
    }

    public int desactiver(Long id) {
        jdbc.execute(
            "UPDATE utilisateurs SET actif = 0 WHERE id = ?",
            (PreparedStatement ps) -> {
                ps.setLong(1, id);
                return ps.execute();
            }
        );
        return 1;
    }

    public int reactiver(Long id) {
        jdbc.execute(
            "UPDATE utilisateurs SET actif = 1 WHERE id = ?",
            (PreparedStatement ps) -> {
                ps.setLong(1, id);
                return ps.execute();
            }
        );
        return 1;
    }
}