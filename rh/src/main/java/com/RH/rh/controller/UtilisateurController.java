package com.RH.rh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.RH.rh.model.Utilisateur;
import com.RH.rh.repository.AgentRepository;
import com.RH.rh.service.UtilisateurService;

@Controller
@RequestMapping("/admin/utilisateurs")
@PreAuthorize("hasRole('ADMIN')")
public class UtilisateurController {

    private final UtilisateurService utilisateurService;
    private final AgentRepository agentRepository;

    public UtilisateurController(UtilisateurService utilisateurService, AgentRepository agentRepository) {
        this.utilisateurService = utilisateurService;
        this.agentRepository = agentRepository;
    }

    @GetMapping
    public String liste(Model model) {
        model.addAttribute("utilisateurs", utilisateurService.listerUtilisateurs());
        return "utilisateurs/liste";
    }

    @GetMapping("/nouveau")
    public String formulaireCreation(Model model) {
        model.addAttribute("utilisateur", new Utilisateur());
        model.addAttribute("agents", agentRepository.findAll());
        return "utilisateurs/formulaire";
    }

    @PostMapping("/nouveau")
    public String creer(@RequestParam String username, @RequestParam String password,
                         @RequestParam String role,
                         @RequestParam(required = false) Long agentId,
                         RedirectAttributes redirect) {
        try {
            utilisateurService.creerUtilisateur(username, password, role, agentId);
            redirect.addFlashAttribute("message", "Utilisateur créé avec succès.");
            return "redirect:/admin/utilisateurs";
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("erreur", ex.getMessage());
            return "redirect:/admin/utilisateurs/nouveau";
        }
    }

    @PostMapping("/{id}/role")
    public String modifierRole(@PathVariable Long id, @RequestParam String role,
                                RedirectAttributes redirect) {
        utilisateurService.modifierRole(id, role);
        redirect.addFlashAttribute("message", "Rôle mis à jour.");
        return "redirect:/admin/utilisateurs";
    }

    @PostMapping("/{id}/mot-de-passe")
    public String changerMotDePasse(@PathVariable Long id, @RequestParam String nouveauMotDePasse,
                                     RedirectAttributes redirect) {
        utilisateurService.changerMotDePasse(id, nouveauMotDePasse);
        redirect.addFlashAttribute("message", "Mot de passe modifié.");
        return "redirect:/admin/utilisateurs";
    }

    @PostMapping("/{id}/desactiver")
    public String desactiver(@PathVariable Long id, RedirectAttributes redirect) {
        utilisateurService.desactiverUtilisateur(id);
        redirect.addFlashAttribute("message", "Utilisateur désactivé.");
        return "redirect:/admin/utilisateurs";
    }

    @PostMapping("/{id}/reactiver")
    public String reactiver(@PathVariable Long id, RedirectAttributes redirect) {
        utilisateurService.reactiverUtilisateur(id);
        redirect.addFlashAttribute("message", "Utilisateur réactivé.");
        return "redirect:/admin/utilisateurs";
    }
}