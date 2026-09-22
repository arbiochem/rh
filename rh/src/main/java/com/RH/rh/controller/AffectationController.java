package com.RH.rh.controller;

import com.RH.rh.model.Agent;
import com.RH.rh.model.AffectationForm;
import com.RH.rh.repository.AgentRepository;
import com.RH.rh.repository.AffectationRepository;
import com.RH.rh.repository.MembreEquipeRepository;
import com.RH.rh.repository.UtilisateurRepository;
import com.RH.rh.service.ExcelExportService;

import jakarta.validation.Valid;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/affectations")
public class AffectationController {

    private final AffectationRepository affectationRepository;
    private final AgentRepository agentRepository;
    private final MembreEquipeRepository membreEquipeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ExcelExportService excelExportService;

    public AffectationController(
            AffectationRepository affectationRepository,
            AgentRepository agentRepository,
            MembreEquipeRepository membreEquipeRepository,
            UtilisateurRepository utilisateurRepository,
            ExcelExportService excelExportService
    ) {
        this.affectationRepository = affectationRepository;
        this.agentRepository = agentRepository;
        this.membreEquipeRepository = membreEquipeRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.excelExportService = excelExportService;
    }

    /**
     * Détermine la liste des agents affectables pour l'utilisateur connecté :
     * - ADMIN : tous les agents.
     * - Chef d'équipe : uniquement les agents des équipes dont il est chef.
     * - Sinon (pas lié à un agent, ou chef d'aucune équipe) : liste vide.
     */
    private List<Agent> resolveAgentsPourUtilisateur(Authentication authentication) {

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));

        if (isAdmin) {
            return agentRepository.findAll();
        }

        Optional<Long> agentIdOpt = utilisateurRepository.findAgentIdByUsername(authentication.getName());
        if (agentIdOpt.isEmpty()) {
            return Collections.emptyList();
        }

        return membreEquipeRepository.findAgentsDesEquipesDontChef(agentIdOpt.get());
    }

    /**
     * =========================================================
     * LISTE + RECHERCHE
     * URL : /affectations
     * =========================================================
     */
    @GetMapping
    public String index(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "debut", required = false) LocalDate debut,
            @RequestParam(value = "fin", required = false) LocalDate fin,
            Model model,
            Authentication authentication
    ) {

        if (debut != null && fin != null && debut.isAfter(fin)) {

            model.addAttribute(
                    "error",
                    "La date de début doit être antérieure ou égale à la date de fin."
            );

            LocalDate temp = debut;
            debut = fin;
            fin = temp;
        }

        AffectationForm form = new AffectationForm();
        form.setDateAffectation(LocalDate.now());
        model.addAttribute("form", form);

        /*
         * Liste des agents restreinte selon le rôle/l'équipe de l'utilisateur connecté
         */
        model.addAttribute("agents", resolveAgentsPourUtilisateur(authentication));

        model.addAttribute("q", q);
        model.addAttribute("debut", debut);
        model.addAttribute("fin", fin);

        List<Map<String, Object>> affectations =
                affectationRepository.search(q, debut, fin);

        model.addAttribute("affectations", affectations);

        return "affectations";
    }


    /**
     * =========================================================
     * ENREGISTREMENT D'UNE AFFECTATION
     * =========================================================
     */
    @PostMapping
    public String save(
            @Valid
            @ModelAttribute("form")
            AffectationForm form,

            BindingResult result,

            Model model,
            Authentication authentication
    ) {

        if (result.hasErrors()) {

            model.addAttribute("agents", resolveAgentsPourUtilisateur(authentication));

            model.addAttribute(
                    "affectations",
                    affectationRepository.findAll()
            );

            model.addAttribute("q", null);
            model.addAttribute("debut", null);
            model.addAttribute("fin", null);

            return "affectations";
        }

        affectationRepository.save(form);

        return "redirect:/affectations";
    }


    /**
     * =========================================================
     * SUPPRESSION
     * =========================================================
     */
    @PostMapping("/{id}/delete")
    public String delete(
            @PathVariable Long id
    ) {

        affectationRepository.delete(id);

        return "redirect:/affectations";
    }


    @GetMapping("/{id}/edit")
    public String edit(
            @PathVariable Long id,
            Model model,
            Authentication authentication
    ) {

        Map<String, Object> affectation =
                affectationRepository.findById(id);

        if (affectation == null) {
            return "redirect:/affectations";
        }

        model.addAttribute("affectation", affectation);

        model.addAttribute("agents", resolveAgentsPourUtilisateur(authentication));

        return "edit_affectation";
    }

    @PostMapping("/edit/{id}")
    public String update(

            @PathVariable Long id,

            @Valid
            @ModelAttribute("affectation")
            AffectationForm affectation,

            BindingResult result

    ) {

        if (result.hasErrors()) {
            return "edit_affectation";
        }

        affectation.setId(id);
        affectationRepository.update(affectation);

        return "redirect:/affectations";
    }

    @GetMapping("/export")
    public ResponseEntity<ByteArrayResource> export(
            @RequestParam LocalDate debut,
            @RequestParam LocalDate fin
    ) throws Exception {

        var rows =
                affectationRepository.findBetween(debut, fin);

        byte[] excel =
                excelExportService.export(rows, debut, fin);

        String filename =
                "Pointage_" + debut + "_au_" + fin + ".xlsx";

        ByteArrayResource resource =
                new ByteArrayResource(excel);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\""
                )
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        )
                )
                .contentLength(excel.length)
                .body(resource);
    }
}