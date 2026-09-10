package com.RH.rh.model;

public class Affectation {

    private Long id;

    private Long agentId;

    private String dateAffectation;

    private String sites;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getSites() {
        return sites;
    }

    public void setSites(String sites) {
        this.sites = sites;
    }


    public String getDateAffectation() {
        return dateAffectation;
    }

    public void setDateAffectation(String dateAffectation) {
        this.dateAffectation = dateAffectation;
    }
}