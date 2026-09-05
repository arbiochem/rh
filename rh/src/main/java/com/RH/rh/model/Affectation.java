package com.RH.rh.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Entity
@Table(name = "affectations")
public class Affectation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "L'agent est obligatoire")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "site_id")
    private Site site;

    /** Lieu libre utilise si l'affectation ne correspond a aucun site enregistre. */
    @Column(name = "lieu_libre")
    private String lieuLibre;

    @NotNull(message = "La date d'affectation est obligatoire")
    @Column(name = "date_affectation", nullable = false)
    private LocalDate dateAffectation;

    @Column(name = "heure_debut")
    private String heureDebut;

    @Column(name = "heure_fin")
    private String heureFin;

    @Column(nullable = false)
    private String statut = "PLANIFIE"; // PLANIFIE, CONFIRME, ANNULE, TERMINE

    private String commentaire;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }

    public Site getSite() { return site; }
    public void setSite(Site site) { this.site = site; }

    public String getLieuLibre() { return lieuLibre; }
    public void setLieuLibre(String lieuLibre) { this.lieuLibre = lieuLibre; }

    public LocalDate getDateAffectation() { return dateAffectation; }
    public void setDateAffectation(LocalDate dateAffectation) { this.dateAffectation = dateAffectation; }

    public String getHeureDebut() { return heureDebut; }
    public void setHeureDebut(String heureDebut) { this.heureDebut = heureDebut; }

    public String getHeureFin() { return heureFin; }
    public void setHeureFin(String heureFin) { this.heureFin = heureFin; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    /** Libelle du lieu affiche : site enregistre en priorite, sinon lieu libre. */
    public String getLieuAffiche() {
        if (site != null) return site.getNom();
        return lieuLibre != null ? lieuLibre : "-";
    }
}
