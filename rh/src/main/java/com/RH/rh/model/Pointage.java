package com.RH.rh.model;

public class Pointage {

    private String telephone;
    private String agent;
    private String site;
    private String date;
    private String heure;
    private String type;

    public Pointage() {
    }

    public Pointage(String telephone,
                    String agent,
                    String site,
                    String heure,
                    String date,
                    String type) {
        this.telephone = telephone;
        this.agent = agent;
        this.site = site;
        this.heure = heure;
        this.date = date;
        this.type = type;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
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

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date= date;
    }

    public String getHeure() {
        return heure;
    }

    public void setHeure(String heure) {
        this.heure = heure;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}