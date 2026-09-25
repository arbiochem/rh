package com.RH.rh.dto;

import java.util.ArrayList;
import java.util.List;

public class PointageAffichage {

    private String telephone;
    private String agent;
    private String equipe;
    private String lieuAffectation;
    private String site;
    private String typeLieu;
    private String date;
    private String nomAgent;
    private String roleEquipe;

    private final List<HeureType> pointages = new ArrayList<>();

    public static class HeureType {
        private final String heure;
        private final String type;

        public HeureType(String heure, String type) {
            this.heure = heure;
            this.type = type;
        }

        public String getHeure() { return heure; }
        public String getType() { return type; }
        public boolean isEntree() { return "Entrée".equals(type); }
    }

    /**
     * Une paire Entrée/Sortie pour l'affichage groupé "14:01 → 15:02".
     * entree ou sortie peuvent être null si l'un des deux manque
     * (ex : dernière entrée du jour sans sortie encore enregistrée).
     */
    public static class Paire {
        private final HeureType entree;
        private final HeureType sortie;

        public Paire(HeureType entree, HeureType sortie) {
            this.entree = entree;
            this.sortie = sortie;
        }

        public HeureType getEntree() { return entree; }
        public HeureType getSortie() { return sortie; }
    }

    public void ajouterPointage(String heure, String type) {
        pointages.add(new HeureType(heure, type));
    }

    public List<HeureType> getPointages() { return pointages; }

    /**
     * Regroupe la liste chronologique de pointages en paires
     * Entrée/Sortie consécutives. Gère les cas où une entrée n'a pas
     * (encore) de sortie associée, ou où une sortie apparaît sans
     * entrée précédente (donnée incohérente mais on l'affiche quand
     * même plutôt que de la perdre).
     */
    public List<Paire> getPaires() {
        List<Paire> paires = new ArrayList<>();
        HeureType enAttente = null;

        for (HeureType h : pointages) {
            if (h.isEntree()) {
                if (enAttente != null) {
                    // Deux entrées consécutives sans sortie entre les deux
                    paires.add(new Paire(enAttente, null));
                }
                enAttente = h;
            } else {
                if (enAttente != null) {
                    paires.add(new Paire(enAttente, h));
                    enAttente = null;
                } else {
                    // Sortie sans entrée précédente
                    paires.add(new Paire(null, h));
                }
            }
        }

        if (enAttente != null) {
            paires.add(new Paire(enAttente, null));
        }

        return paires;
    }

    public Paire getPremierePaire() {
        List<Paire> paires = getPaires();
        return paires.isEmpty() ? null : paires.get(0);
    }

    public List<Paire> getPairesRestantes() {
        List<Paire> paires = getPaires();
        return paires.size() > 1 ? paires.subList(1, paires.size()) : List.of();
    }

    public int getNombrePairesSupplementaires() {
        return Math.max(0, getPaires().size() - 1);
    }

    /**
     * Texte complet affiché en infobulle (title) au survol de la cellule
     * Heure, sur le modèle de la colonne Affectation.
     * Exemple : "14:01 → 15:02 | 16:10 → 17:00"
     */
    public String getHeuresTitre() {
        StringBuilder sb = new StringBuilder();
        List<Paire> paires = getPaires();

        for (int i = 0; i < paires.size(); i++) {
            Paire p = paires.get(i);
            if (i > 0) sb.append(" | ");

            String e = p.getEntree() != null ? p.getEntree().getHeure() : "?";
            String s = p.getSortie() != null ? p.getSortie().getHeure() : "?";
            sb.append(e).append(" → ").append(s);
        }

        return sb.toString();
    }

    /**
     * Date reformatée en dd/MM/yyyy pour l'affichage. Le champ "date" est
     * une String au format yyyy/MM/dd ou yyyy-MM-dd (venant du repository),
     * donc pas de #dates.format possible côté Thymeleaf.
     */
    public String getDateAffichage() {
        if (date == null || date.isBlank()) {
            return "-";
        }

        String d = date.trim();
        String[] parties = d.contains("/") ? d.split("/") : d.split("-");

        if (parties.length == 3 && parties[0].length() == 4) {
            return parties[2] + "/" + parties[1] + "/" + parties[0];
        }

        return d;
    }

    public HeureType getPremier() {
        return pointages.isEmpty() ? null : pointages.get(0);
    }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }

    public String getAgent() { return agent; }
    public void setAgent(String agent) { this.agent = agent; }

    public String getEquipe() { return equipe; }
    public void setEquipe(String equipe) { this.equipe = equipe; }

    public String getLieuAffectation() { return lieuAffectation; }
    public void setLieuAffectation(String lieuAffectation) { this.lieuAffectation = lieuAffectation; }

    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }

    public String getTypeLieu() { return typeLieu; }
    public void setTypeLieu(String typeLieu) { this.typeLieu = typeLieu; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getNomAgent() { return nomAgent; }
    public void setNomAgent(String nomAgent) { this.nomAgent = nomAgent; }

    public String getRoleEquipe() { return roleEquipe; }
    public void setRoleEquipe(String roleEquipe) { this.roleEquipe = roleEquipe; }
}