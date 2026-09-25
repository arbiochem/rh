package com.RH.rh.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.RH.rh.model.Equipe;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class EquipeRepository {

    private final JdbcTemplate jdbc;

    public EquipeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Equipe mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Equipe e = new Equipe();
        e.setId(rs.getLong("id"));
        e.setNom(rs.getString("nom"));
        e.setDescription(rs.getString("description"));
        Timestamp ts = rs.getTimestamp("date_creation");
        if (ts != null) {
            e.setDateCreation(ts.toLocalDateTime());
        }
        e.setActif(rs.getInt("actif") == 1);
        return e;
    }

    public List<Equipe> findAll() {
        String sql = "SELECT * FROM equipes WHERE actif = 1 ORDER BY nom";
        return jdbc.query(sql, this::mapRow);
    }

    public Optional<Equipe> findById(Long id) {
        String sql = "SELECT * FROM equipes WHERE id = ?";
        try {
            Equipe e = jdbc.queryForObject(sql, this::mapRow, id);
            return Optional.ofNullable(e);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Equipe save(Equipe equipe) {

        // SQL Server : OUTPUT INSERTED.id renvoie l'id généré dans la même
        // requête (SCOPE_IDENTITY() dépendrait de la connexion du pool).
        Long newId = jdbc.queryForObject(
            """
            INSERT INTO equipes (nom, description, date_creation, actif)
            OUTPUT INSERTED.id
            VALUES (?, ?, ?, 1)
            """,
            Long.class,
            equipe.getNom(),
            equipe.getDescription(),
            Timestamp.valueOf(LocalDateTime.now())
        );

        equipe.setId(newId);

        return equipe;
    }

    public int update(Long id, String nom, String description) {
        jdbc.execute(
            "UPDATE equipes SET nom = ?, description = ? WHERE id = ?",
            (PreparedStatement ps) -> {
                ps.setString(1, nom);
                ps.setString(2, description);
                ps.setLong(3, id);
                return ps.execute();
            }
        );
        return 1;
    }

    /** Suppression douce : on désactive l'équipe plutôt que de la supprimer physiquement. */
    public int desactiver(Long id) {
        jdbc.execute(
            "UPDATE equipes SET actif = 0 WHERE id = ?",
            (PreparedStatement ps) -> {
                ps.setLong(1, id);
                return ps.execute();
            }
        );
        return 1;
    }

    /** Suppression définitive (supprime aussi les membres via ON DELETE CASCADE). */
    public int deleteHard(Long id) {
        jdbc.execute(
            "DELETE FROM equipes WHERE id = ?",
            (PreparedStatement ps) -> {
                ps.setLong(1, id);
                return ps.execute();
            }
        );
        return 1;
    }

    public int reactiver(Long id) {
        jdbc.execute(
            "UPDATE equipes SET actif = 1 WHERE id = ?",
            (PreparedStatement ps) -> {
                ps.setLong(1, id);
                return ps.execute();
            }
        );
        return 1;
    }

    public List<Equipe> findAllInactives() {
        String sql = "SELECT * FROM equipes WHERE actif = 0 ORDER BY nom";
        return jdbc.query(sql, this::mapRow);
    }

    public boolean existsByNom(String nom) {
        String sql = "SELECT COUNT(*) FROM equipes WHERE nom = ?";
        Integer count = jdbc.queryForObject(sql, Integer.class, nom);
        return count != null && count > 0;
    }
}