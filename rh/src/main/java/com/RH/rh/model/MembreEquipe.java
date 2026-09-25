package com.RH.rh.model;

import java.time.LocalDateTime;

/**
 * Représente l'appartenance d'un agent à une équipe.
 */
public class MembreEquipe {

    public enum RoleEquipe {
        CHEF,
        MEMBRE
    }

    private Long id;

    private Long equipeId;

    private Long agentId;

    private RoleEquipe roleEquipe = RoleEquipe.MEMBRE;

    private LocalDateTime dateAjout;

    // Champs enrichis pour l'affichage
    // provenant de la table agents
    private String nomAgent;

    private String prenomAgent;

    private String emailAgent;

    private String telephoneAgent;

    public MembreEquipe() {
    }

    public MembreEquipe(
            Long equipeId,
            Long agentId,
            RoleEquipe roleEquipe) {

        this.equipeId = equipeId;
        this.agentId = agentId;
        this.roleEquipe = roleEquipe;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEquipeId() {
        return equipeId;
    }

    public void setEquipeId(Long equipeId) {
        this.equipeId = equipeId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public RoleEquipe getRoleEquipe() {
        return roleEquipe;
    }

    public void setRoleEquipe(RoleEquipe roleEquipe) {
        this.roleEquipe = roleEquipe;
    }

    public LocalDateTime getDateAjout() {
        return dateAjout;
    }

    public void setDateAjout(LocalDateTime dateAjout) {
        this.dateAjout = dateAjout;
    }

    public String getNomAgent() {
        return nomAgent;
    }

    public void setNomAgent(String nomAgent) {
        this.nomAgent = nomAgent;
    }

    public String getPrenomAgent() {
        return prenomAgent;
    }

    public void setPrenomAgent(String prenomAgent) {
        this.prenomAgent = prenomAgent;
    }

    public String getEmailAgent() {
        return emailAgent;
    }

    public void setEmailAgent(String emailAgent) {
        this.emailAgent = emailAgent;
    }

    public String getTelephoneAgent() {
        return telephoneAgent;
    }

    public void setTelephoneAgent(String telephoneAgent) {
        this.telephoneAgent = telephoneAgent;
    }
}