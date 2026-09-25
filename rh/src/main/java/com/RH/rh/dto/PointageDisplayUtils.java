package com.RH.rh.dto;

import com.RH.rh.model.Pointage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PointageDisplayUtils {

    /**
     * Regroupe les pointages d'un même agent, sur un même site, une même
     * date, en une seule ligne d'affichage listant toutes les heures avec
     * leur type (Entrée/Sortie) pour la coloration individuelle.
     */
    public static List<PointageAffichage> grouperParLieu(List<Pointage> pointages) {

        Map<String, PointageAffichage> parCle = new LinkedHashMap<>();

        for (Pointage p : pointages) {
            String cle = cleGroupe(p);

            PointageAffichage groupe = parCle.get(cle);
            if (groupe == null) {
                groupe = new PointageAffichage();
                groupe.setTelephone(p.getTelephone());
                groupe.setAgent(p.getAgent());
                groupe.setEquipe(p.getEquipe());
                groupe.setLieuAffectation(p.getLieuAffectation());
                groupe.setSite(p.getSite());
                groupe.setTypeLieu(p.getTypeLieu());
                groupe.setDate(p.getDate());
                groupe.setNomAgent(p.getNomAgent());
                groupe.setRoleEquipe(p.getRoleEquipe());
                parCle.put(cle, groupe);
            }

            groupe.ajouterPointage(p.getHeure(), p.getType());
        }

        return new ArrayList<>(parCle.values());
    }

    private static String cleGroupe(Pointage p) {
        return safe(p.getTelephone()) + "|" + safe(p.getSite()) + "|" + safe(p.getDate());
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}