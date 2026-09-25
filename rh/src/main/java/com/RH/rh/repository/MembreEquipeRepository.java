package com.RH.rh.repository;

import com.RH.rh.model.MembreEquipe;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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

    /**
     * Récupère tous les membres d'une équipe.
     *
     * Relation :
     * membres_equipe.agent_id -> agents.id
     */
    public List<MembreEquipe> findByEquipeId(Long equipeId) {

        String sql = """
            SELECT
                me.id,
                me.equipe_id,
                me.agent_id,
                me.role_equipe,
                me.date_ajout,
                a.nom AS nom,
                a.prenom AS prenom,
                a.email AS email,
                a.telephone AS telephone
            FROM membres_equipe me
            JOIN agents a
                ON a.id = me.agent_id
            WHERE me.equipe_id = ?
            ORDER BY me.role_equipe, a.nom, a.prenom
            """;

        return jdbc.query(
            sql,
            (rs, rowNum) -> {

                MembreEquipe membre = new MembreEquipe();

                membre.setId(rs.getLong("id"));
                membre.setEquipeId(rs.getLong("equipe_id"));
                membre.setAgentId(rs.getLong("agent_id"));

                String role = rs.getString("role_equipe");

                if (role != null && !role.isBlank()) {
                    membre.setRoleEquipe(
                        MembreEquipe.RoleEquipe.valueOf(role)
                    );
                } else {
                    membre.setRoleEquipe(
                        MembreEquipe.RoleEquipe.MEMBRE
                    );
                }

                Timestamp timestamp =
                    rs.getTimestamp("date_ajout");

                if (timestamp != null) {
                    membre.setDateAjout(
                        timestamp.toLocalDateTime()
                    );
                }

                membre.setNomAgent(
                    rs.getString("nom")
                );

                membre.setPrenomAgent(
                    rs.getString("prenom")
                );

                membre.setEmailAgent(
                    rs.getString("email")
                );

                membre.setTelephoneAgent(
                    rs.getString("telephone")
                );

                return membre;
            },
            equipeId
        );
    }

    /**
     * Récupère les équipes auxquelles appartient un agent.
     */
    public List<MembreEquipe> findByAgentId(Long agentId) {

        String sql = """
            SELECT
                me.id,
                me.equipe_id,
                me.agent_id,
                me.role_equipe,
                me.date_ajout,
                a.nom AS nom,
                a.prenom AS prenom,
                a.email AS email,
                a.telephone AS telephone
            FROM membres_equipe me
            JOIN agents a
                ON a.id = me.agent_id
            WHERE me.agent_id = ?
            ORDER BY me.date_ajout DESC
            """;

        return jdbc.query(
            sql,
            (rs, rowNum) -> {

                MembreEquipe membre = new MembreEquipe();

                membre.setId(rs.getLong("id"));
                membre.setEquipeId(rs.getLong("equipe_id"));
                membre.setAgentId(rs.getLong("agent_id"));

                String role = rs.getString("role_equipe");

                if (role != null && !role.isBlank()) {
                    membre.setRoleEquipe(
                        MembreEquipe.RoleEquipe.valueOf(role)
                    );
                } else {
                    membre.setRoleEquipe(
                        MembreEquipe.RoleEquipe.MEMBRE
                    );
                }

                Timestamp timestamp =
                    rs.getTimestamp("date_ajout");

                if (timestamp != null) {
                    membre.setDateAjout(
                        timestamp.toLocalDateTime()
                    );
                }

                membre.setNomAgent(
                    rs.getString("nom")
                );

                membre.setPrenomAgent(
                    rs.getString("prenom")
                );

                membre.setEmailAgent(
                    rs.getString("email")
                );

                membre.setTelephoneAgent(
                    rs.getString("telephone")
                );

                return membre;
            },
            agentId
        );
    }

    /**
     * Ajoute un agent dans une équipe.
     */
    public MembreEquipe ajouterMembre(
            Long equipeId,
            Long agentId,
            MembreEquipe.RoleEquipe role) {

        String sql = """
            INSERT INTO membres_equipe
                (equipe_id, agent_id, role_equipe, date_ajout)
            VALUES (?, ?, ?, ?)
            """;

        jdbc.execute(
            sql,
            (PreparedStatement ps) -> {

                ps.setLong(1, equipeId);
                ps.setLong(2, agentId);
                ps.setString(3, role.name());
                ps.setTimestamp(
                    4,
                    Timestamp.valueOf(
                        LocalDateTime.now()
                    )
                );

                return ps.execute();
            }
        );

        return new MembreEquipe(
            equipeId,
            agentId,
            role
        );
    }

    /**
     * Vérifie si un agent appartient déjà à une équipe.
     */
    public boolean existeMembre(
            Long equipeId,
            Long agentId) {

        String sql = """
            SELECT COUNT(*)
            FROM membres_equipe
            WHERE equipe_id = ?
              AND agent_id = ?
            """;

        Integer count = jdbc.queryForObject(
            sql,
            Integer.class,
            equipeId,
            agentId
        );

        return count != null && count > 0;
    }

    /**
     * Modifie le rôle d'un agent dans une équipe.
     */
    public int changerRole(
            Long equipeId,
            Long agentId,
            MembreEquipe.RoleEquipe role) {

        String sql = """
            UPDATE membres_equipe
            SET role_equipe = ?
            WHERE equipe_id = ?
            AND agent_id = ?
            """;

        return jdbc.update(
            sql,
            role.name(),
            equipeId,
            agentId
        );
    }

    /**
     * Retire un agent d'une équipe.
     */
    public int retirerMembre(
            Long equipeId,
            Long agentId) {

        String sql = """
            DELETE FROM membres_equipe
            WHERE equipe_id = ?
              AND agent_id = ?
            """;

        return jdbc.update(
            sql,
            equipeId,
            agentId
        );
    }

    /**
     * Compte le nombre de membres d'une équipe.
     */
    public int compterMembres(Long equipeId) {

        String sql = """
            SELECT COUNT(*)
            FROM membres_equipe
            WHERE equipe_id = ?
            """;

        Integer count = jdbc.queryForObject(
            sql,
            Integer.class,
            equipeId
        );

        return count != null ? count : 0;
    }

    /**
     * Récupère les agents des équipes dont l'agent donné
     * est chef.
     */
    public List<com.RH.rh.model.Agent>
            findAgentsDesEquipesDontChef(Long agentChefId) {

        String sql = """
            SELECT DISTINCT
                a.id,
                a.matricule,
                a.nom,
                a.prenom,
                a.telephone,
                a.email,
                a.poste,
                a.actif,
                a.created_at
            FROM membres_equipe me
            JOIN agents a
                ON a.id = me.agent_id
            WHERE me.equipe_id IN (
                SELECT equipe_id
                FROM membres_equipe
                WHERE agent_id = ?
                  AND role_equipe = 'CHEF'
            )
            AND me.agent_id <> ?
            ORDER BY a.nom, a.prenom
            """;

        return jdbc.query(
            sql,
            (rs, rowNum) -> {

                com.RH.rh.model.Agent agent =
                    new com.RH.rh.model.Agent();

                agent.setId(rs.getLong("id"));
                agent.setMatricule(
                    rs.getString("matricule")
                );
                agent.setNom(
                    rs.getString("nom")
                );
                agent.setPrenom(
                    rs.getString("prenom")
                );
                agent.setTelephone(
                    rs.getString("telephone")
                );
                agent.setEmail(
                    rs.getString("email")
                );

                Boolean actif =
                    rs.getBoolean("actif");

                if (!rs.wasNull()) {
                    agent.setActif(actif);
                }

                Timestamp createdAt =
                    rs.getTimestamp("created_at");

                return agent;
            },
            agentChefId,
            agentChefId
        );
    }
}