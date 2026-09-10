package com.RH.rh.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class AffectationForm {

    private Long id;

    @NotNull(message = "Veuillez sélectionner un agent")
    private Long agentId;

    @NotNull(message = "Veuillez sélectionner une date")
    private LocalDate dateAffectation;

    @NotEmpty(message = "Saisissez au moins un site")
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


    public LocalDate getDateAffectation() {
        return dateAffectation;
    }

    public void setDateAffectation(LocalDate dateAffectation) {
        this.dateAffectation = dateAffectation;
    }


    public String getSites() {
        return sites;
    }

    public void setSites(String sites) {
        this.sites = sites;
    }
}