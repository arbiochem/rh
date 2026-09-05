package com.RH.rh.controller;

import com.RH.rh.model.Affectation;
import com.RH.rh.model.Agent;
import com.RH.rh.model.Site;
import com.RH.rh.repository.AffectationRepository;
import com.RH.rh.repository.AgentRepository;
import com.RH.rh.repository.SiteRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/affectations")
public class AffectationController {

    private final AffectationRepository affectationRepository;
    private final AgentRepository agentRepository;
    private final SiteRepository siteRepository;

    public AffectationController(AffectationRepository affectationRepository,
                                  AgentRepository agentRepository,
                                  SiteRepository siteRepository) {
        this.affectationRepository = affectationRepository;
        this.agentRepository = agentRepository;
        this.siteRepository = siteRepository;
    }

    @GetMapping
    public String liste(@RequestParam(required = false) String date, Model model) {
        LocalDate jour = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        model.addAttribute("affectations", affectationRepository.findByDateAffectation(jour));
        model.addAttribute("jour", jour);
        return "affectations/list";
    }

    @GetMapping("/nouveau")
    public String formulaireNouveau(Model model) {
        model.addAttribute("agents", agentRepository.findAll());
        model.addAttribute("sites", siteRepository.findAll());
        model.addAttribute("aujourdHui", LocalDate.now());
        return "affectations/form";
    }

    /**
     * Cree l'affectation journaliere d'un agent : un enregistrement est cree
     * pour chaque site coche, plus un enregistrement supplementaire si un
     * lieu libre (hors liste des sites) est renseigne.
     */
    @PostMapping("/enregistrer")
    public String enregistrer(@RequestParam Long agentId,
                               @RequestParam String dateAffectation,
                               @RequestParam(required = false) List<Long> siteIds,
                               @RequestParam(required = false) String lieuLibre,
                               @RequestParam(required = false) String heureDebut,
                               @RequestParam(required = false) String heureFin,
                               @RequestParam(required = false) String statut,
                               @RequestParam(required = false) String commentaire) {

        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent introuvable : " + agentId));
        LocalDate date = LocalDate.parse(dateAffectation);

        List<Affectation> aCreer = new ArrayList<>();

        if (siteIds != null) {
            for (Long siteId : siteIds) {
                Site site = siteRepository.findById(siteId).orElse(null);
                if (site == null) continue;
                Affectation a = new Affectation();
                a.setAgent(agent);
                a.setSite(site);
                a.setDateAffectation(date);
                a.setHeureDebut(heureDebut);
                a.setHeureFin(heureFin);
                a.setStatut(statut != null && !statut.isBlank() ? statut : "PLANIFIE");
                a.setCommentaire(commentaire);
                aCreer.add(a);
            }
        }

        if (lieuLibre != null && !lieuLibre.isBlank()) {
            Affectation a = new Affectation();
            a.setAgent(agent);
            a.setLieuLibre(lieuLibre);
            a.setDateAffectation(date);
            a.setHeureDebut(heureDebut);
            a.setHeureFin(heureFin);
            a.setStatut(statut != null && !statut.isBlank() ? statut : "PLANIFIE");
            a.setCommentaire(commentaire);
            aCreer.add(a);
        }

        affectationRepository.saveAll(aCreer);
        return "redirect:/affectations?date=" + date;
    }

    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id, @RequestParam(required = false) String date) {
        affectationRepository.deleteById(id);
        return "redirect:/affectations" + (date != null ? "?date=" + date : "");
    }
}
