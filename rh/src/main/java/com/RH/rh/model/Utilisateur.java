package com.RH.rh.model;

public class Utilisateur {

    private Long id;
    private String username;
    private String password;
    private String role;
    private boolean actif;
    private Long agentId;          // ← ajouté, nullable

    // Champs enrichis par jointure (findAll), pas stockés en base
    private String agentNom;       // ← ajouté
    private String agentPrenom;    // ← ajouté

    public Utilisateur() {
    }

    public Utilisateur(Long id, String username, String password, String role, boolean actif) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.actif = actif;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getAgentNom() { return agentNom; }
    public void setAgentNom(String agentNom) { this.agentNom = agentNom; }

    public String getAgentPrenom() { return agentPrenom; }
    public void setAgentPrenom(String agentPrenom) { this.agentPrenom = agentPrenom; }
}