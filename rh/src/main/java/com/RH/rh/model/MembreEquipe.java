package com.RH.rh.model;

import java.time.LocalDateTime;

/**
 * Représente l'appartenance d'un utilisateur à une équipe.
 */
public class MembreEquipe {

    public enum RoleEquipe {
        CHEF,
        MEMBRE
    }

    private Long id;
    private Long equipeId;
    private Long utilisateurId;
    private RoleEquipe roleEquipe = RoleEquipe.MEMBRE;
    private LocalDateTime dateAjout;

    // Champs enrichis pour l'affichage (jointure avec la table utilisateurs)
    private String nomUtilisateur;
    private String emailUtilisateur;

    public MembreEquipe() {
    }

    public MembreEquipe(Long equipeId, Long utilisateurId, RoleEquipe roleEquipe) {
        this.equipeId = equipeId;
        this.utilisateurId = utilisateurId;
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

    public Long getUtilisateurId() {
        return utilisateurId;
    }

    public void setUtilisateurId(Long utilisateurId) {
        this.utilisateurId = utilisateurId;
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

    public String getNomUtilisateur() {
        return nomUtilisateur;
    }

    public void setNomUtilisateur(String nomUtilisateur) {
        this.nomUtilisateur = nomUtilisateur;
    }

    public String getEmailUtilisateur() {
        return emailUtilisateur;
    }

    public void setEmailUtilisateur(String emailUtilisateur) {
        this.emailUtilisateur = emailUtilisateur;
    }
}