package com.RH.rh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.RH.rh.model.Equipe;
import com.RH.rh.model.MembreEquipe;
import com.RH.rh.repository.AgentRepository;
import com.RH.rh.service.EquipeService;

/**
 * Contrôleur MVC (rendu Thymeleaf) pour l'écran d'administration des équipes.
 * Distinct du EquipeController REST — celui-ci sert des pages HTML complètes.
 */
@Controller
@RequestMapping("/admin/equipes")
@PreAuthorize("hasRole('ADMIN')")
public class EquipeController {

    private final EquipeService equipeService;
    private final AgentRepository agentRepository;  // ← à ajouter

    public EquipeController(EquipeService equipeService, AgentRepository agentRepository) {  // ← modifié
        this.equipeService = equipeService;
        this.agentRepository = agentRepository;  // ← à ajouter
    }

    @GetMapping
    public String liste(Model model) {
        model.addAttribute("equipes", equipeService.listerEquipes());
        return "equipes/liste";
    }

    @GetMapping("/nouveau")
    public String formulaireCreation(Model model) {
        model.addAttribute("equipe", new Equipe());
        return "equipes/formulaire";
    }

    @PostMapping("/nouveau")
    public String creer(@RequestParam String nom, @RequestParam(required = false) String description,
                         RedirectAttributes redirect) {
        try {
            equipeService.creerEquipe(nom, description);
            redirect.addFlashAttribute("message", "Équipe créée avec succès.");
            return "redirect:/admin/equipes";
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("erreur", ex.getMessage());
            return "redirect:/admin/equipes/nouveau";
        }
    }

    @GetMapping("/{id}")
    public String details(@PathVariable Long id, Model model) {
        Equipe equipe = equipeService.obtenirEquipe(id);
        model.addAttribute("equipe", equipe);
        model.addAttribute("membres", equipe.getMembres());
        model.addAttribute("agents", agentRepository.findAll()); // À ajouter
        return "equipes/details";
    }

    @GetMapping("/{id}/modifier")
    public String formulaireEdition(@PathVariable Long id, Model model) {
        model.addAttribute("equipe", equipeService.obtenirEquipe(id));
        return "equipes/formulaire";
    }

    @PostMapping("/{id}/modifier")
    public String modifier(@PathVariable Long id, @RequestParam String nom,
                            @RequestParam(required = false) String description,
                            RedirectAttributes redirect) {
        try {
            equipeService.modifierEquipe(id, nom, description);
            redirect.addFlashAttribute("message", "Équipe modifiée avec succès.");
            return "redirect:/admin/equipes/" + id;
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("erreur", ex.getMessage());
            return "redirect:/admin/equipes/" + id + "/modifier";
        }
    }

    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id, RedirectAttributes redirect) {
        equipeService.supprimerEquipe(id, false); // désactivation douce
        redirect.addFlashAttribute("message", "Équipe désactivée.");
        return "redirect:/admin/equipes";
    }

    @PostMapping("/{id}/membres")
    public String ajouterMembre(@PathVariable Long id, @RequestParam Long utilisateurId,
                                 @RequestParam String role, RedirectAttributes redirect) {
        try {
            equipeService.ajouterMembre(id, utilisateurId, MembreEquipe.RoleEquipe.valueOf(role));
            redirect.addFlashAttribute("message", "Membre ajouté.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("erreur", ex.getMessage());
        }
        return "redirect:/admin/equipes/" + id;
    }

    @PostMapping("/{id}/membres/{utilisateurId}/role")
    public String changerRole(@PathVariable Long id, @PathVariable Long utilisateurId,
                               @RequestParam String role, RedirectAttributes redirect) {
        equipeService.changerRoleMembre(id, utilisateurId, MembreEquipe.RoleEquipe.valueOf(role));
        redirect.addFlashAttribute("message", "Rôle mis à jour.");
        return "redirect:/admin/equipes/" + id;
    }

    @PostMapping("/{id}/membres/{utilisateurId}/retirer")
    public String retirerMembre(@PathVariable Long id, @PathVariable Long utilisateurId,
                                 RedirectAttributes redirect) {
        equipeService.retirerMembre(id, utilisateurId);
        redirect.addFlashAttribute("message", "Membre retiré de l'équipe.");
        return "redirect:/admin/equipes/" + id;
    }

    @PostMapping("/{id}/reactiver")
    public String reactiver(@PathVariable Long id, RedirectAttributes redirect) {
        equipeService.reactiverEquipe(id);
        redirect.addFlashAttribute("message", "Équipe réactivée.");
        return "redirect:/admin/equipes";
    }

    @GetMapping("/archivees")
    public String equipesArchivees(Model model) {
        model.addAttribute("equipes", equipeService.listerEquipesInactives());
        return "equipes/liste-archivees";
    }
}
