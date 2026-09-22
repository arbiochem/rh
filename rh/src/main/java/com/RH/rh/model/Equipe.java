package com.RH.rh.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Représente une équipe. Simple POJO — aucune annotation JPA/Hibernate requise,
 * car la persistance est gérée manuellement via JdbcTemplate.
 */
public class Equipe {

    private Long id;
    private String nom;
    private String description;
    private LocalDateTime dateCreation;
    private boolean actif = true;

    // Optionnel : rempli uniquement quand on charge une équipe avec ses membres
    private List<MembreEquipe> membres= new ArrayList<>();

    public Equipe() {
    }

    public Equipe(String nom, String description) {
        this.nom = nom;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }

    public List<MembreEquipe> getMembres() {
        return membres;
    }

    public void setMembres(List<MembreEquipe> membres) {
        this.membres = membres;
    }
}