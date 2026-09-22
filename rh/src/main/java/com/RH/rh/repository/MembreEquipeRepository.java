package com.RH.rh.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.RH.rh.model.Agent;
import com.RH.rh.model.MembreEquipe;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class MembreEquipeRepository {

    private final JdbcTemplate jdbc;

    public MembreEquipeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private MembreEquipe mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        MembreEquipe m = new MembreEquipe();
        m.setId(rs.getLong("id"));
        m.setEquipeId(rs.getLong("equipe_id"));
        m.setUtilisateurId(rs.getLong("utilisateur_id"));
        m.setRoleEquipe(MembreEquipe.RoleEquipe.valueOf(rs.getString("role_equipe")));
        Timestamp ts = rs.getTimestamp("date_ajout");
        if (ts != null) {
            m.setDateAjout(ts.toLocalDateTime());
        }
        try {
            m.setNomUtilisateur(rs.getString("nom"));
            m.setEmailUtilisateur(rs.getString("email"));
        } catch (java.sql.SQLException ignored) {
        }
        return m;
    }

    private Agent mapAgentRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Agent agent = new Agent();
        agent.setId(rs.getLong("id"));
        agent.setMatricule(rs.getString("matricule"));
        agent.setNom(rs.getString("nom"));
        agent.setPrenom(rs.getString("prenom"));
        agent.setTelephone(rs.getString("telephone"));
        agent.setEmail(rs.getString("email"));
        return agent;
    }

    /** Liste les membres d'une équipe, enrichis avec leurs infos utilisateur. */
    public List<MembreEquipe> findByEquipeId(Long equipeId) {
        String sql = """
            SELECT me.*, a.nom AS nom, a.email AS email
            FROM membres_equipe me
            JOIN agents a ON a.id = me.utilisateur_id
            WHERE me.equipe_id = ?
            ORDER BY me.role_equipe, a.nom
            """;
        return jdbc.query(sql, this::mapRow, equipeId);
    }

    /** Liste toutes les équipes auxquelles appartient un utilisateur. */
    public List<MembreEquipe> findByUtilisateurId(Long utilisateurId) {
        String sql = "SELECT * FROM membres_equipe WHERE utilisateur_id = ?";
        return jdbc.query(sql, this::mapRow, utilisateurId);
    }

    /**
     * Liste tous les agents membres des équipes dont l'agent donné (agentChefId)
     * est chef ('CHEF'). Utilisé pour restreindre les affectations à un chef d'équipe.
     */
    public List<Agent> findAgentsDesEquipesDontChef(Long agentChefId) {

        String sql = """
            SELECT DISTINCT a.*
            FROM membres_equipe me
            JOIN agents a ON a.id = me.utilisateur_id
            WHERE me.equipe_id IN (
                SELECT equipe_id
                FROM membres_equipe
                WHERE utilisateur_id = ?
                AND role_equipe = 'CHEF'
            )
            AND me.utilisateur_id <> ?
            ORDER BY a.nom
            """;

        return jdbc.query(sql, this::mapAgentRow, agentChefId,
        agentChefId);
    }

    public MembreEquipe ajouterMembre(Long equipeId, Long utilisateurId, MembreEquipe.RoleEquipe role) {
        jdbc.execute(
            """
            INSERT INTO membres_equipe (equipe_id, utilisateur_id, role_equipe, date_ajout)
            VALUES (?, ?, ?, ?)
            """,
            (PreparedStatement ps) -> {
                ps.setLong(1, equipeId);
                ps.setLong(2, utilisateurId);
                ps.setString(3, role.name());
                ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                return ps.execute();
            }
        );
        return new MembreEquipe(equipeId, utilisateurId, role);
    }

    public int changerRole(Long equipeId, Long utilisateurId, MembreEquipe.RoleEquipe nouveauRole) {
        jdbc.execute(
            "UPDATE membres_equipe SET role_equipe = ? WHERE equipe_id = ? AND utilisateur_id = ?",
            (PreparedStatement ps) -> {
                ps.setString(1, nouveauRole.name());
                ps.setLong(2, equipeId);
                ps.setLong(3, utilisateurId);
                return ps.execute();
            }
        );
        return 1;
    }

    public int retirerMembre(Long equipeId, Long utilisateurId) {
        jdbc.execute(
            "DELETE FROM membres_equipe WHERE equipe_id = ? AND utilisateur_id = ?",
            (PreparedStatement ps) -> {
                ps.setLong(1, equipeId);
                ps.setLong(2, utilisateurId);
                return ps.execute();
            }
        );
        return 1;
    }

    public int compterMembres(Long equipeId) {
        String sql = "SELECT COUNT(*) FROM membres_equipe WHERE equipe_id = ?";
        Integer count = jdbc.queryForObject(sql, Integer.class, equipeId);
        return count != null ? count : 0;
    }
}