
package com.RH.rh.dto;

public class PointageJourDTO {

    private String matricule;
    private String agent;
    private String site;
    private String heureDebut;
    private String heureFin;

    public PointageJourDTO() {
    }

    public PointageJourDTO(
            String matricule,
            String agent,
            String site,
            String heureDebut,
            String heureFin) {

        this.matricule = matricule;
        this.agent = agent;
        this.site = site;
        this.heureDebut = heureDebut;
        this.heureFin = heureFin;
    }

    public String getMatricule() {
        return matricule;
    }

    public void setMatricule(String matricule) {
        this.matricule = matricule;
    }

    public String getAgent() {
        return agent;
    }

    public void setAgent(String agent) {
        this.agent = agent;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getHeureDebut() {
        return heureDebut;
    }

    public void setHeureDebut(String heureDebut) {
        this.heureDebut = heureDebut;
    }

    public String getHeureFin() {
        return heureFin;
    }

    public void setHeureFin(String heureFin) {
        this.heureFin = heureFin;
    }
}

