package com.RH.rh.controller;

import com.RH.rh.model.AffectationForm;
import com.RH.rh.repository.AgentRepository;
import com.RH.rh.repository.AffectationRepository;
import com.RH.rh.service.ExcelExportService;

import jakarta.validation.Valid;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/affectations")
public class AffectationController {

    private final AffectationRepository affectationRepository;
    private final AgentRepository agentRepository;
    private final ExcelExportService excelExportService;

    public AffectationController(
            AffectationRepository affectationRepository,
            AgentRepository agentRepository,
            ExcelExportService excelExportService
    ) {
        this.affectationRepository = affectationRepository;
        this.agentRepository = agentRepository;
        this.excelExportService = excelExportService;
    }

    /**
     * =========================================================
     * LISTE + RECHERCHE
     * URL : /affectations
     *
     * Exemples :
     * /affectations
     * /affectations?q=Dupont
     * /affectations?debut=2026-09-01&fin=2026-09-30
     * /affectations?q=Dupont&debut=2026-09-01&fin=2026-09-30
     * =========================================================
     */
    @GetMapping
    public String index(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "debut", required = false) LocalDate debut,
            @RequestParam(value = "fin", required = false) LocalDate fin,
            Model model
    ) {

        /*
         * Vérification de la plage de dates
         */
        if (debut != null && fin != null && debut.isAfter(fin)) {

            model.addAttribute(
                    "error",
                    "La date de début doit être antérieure ou égale à la date de fin."
            );

            /*
             * On inverse temporairement pour éviter
             * une requête SQL incorrecte.
             */
            LocalDate temp = debut;
            debut = fin;
            fin = temp;
        }

        /*
         * Formulaire d'affectation
         */
        AffectationForm form = new AffectationForm();

        /*
         * Date par défaut du formulaire d'affectation
         */
        form.setDateAffectation(
                LocalDate.now()
        );

        model.addAttribute(
                "form",
                form
        );

        /*
         * Liste des agents
         */
        model.addAttribute(
                "agents",
                agentRepository.findAll()
        );

        /*
         * Paramètres de recherche
         *
         * IMPORTANT :
         * Ces attributs sont nécessaires pour :
         *
         * th:value="${q}"
         * th:value="${debut}"
         * th:value="${fin}"
         */
        model.addAttribute(
                "q",
                q
        );

        model.addAttribute(
                "debut",
                debut
        );

        model.addAttribute(
                "fin",
                fin
        );

        /*
         * Recherche
         *
         * On utilise TOUJOURS la méthode search().
         * Si q/debut/fin sont null, elle retourne
         * toutes les affectations.
         */
        List<Map<String, Object>> affectations =
                affectationRepository.search(
                        q,
                        debut,
                        fin
                );

        model.addAttribute(
                "affectations",
                affectations
        );

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

            Model model
    ) {

        if (result.hasErrors()) {

            /*
             * Recharger les agents
             */
            model.addAttribute(
                    "agents",
                    agentRepository.findAll()
            );

            /*
             * Recharger la liste des affectations
             */
            model.addAttribute(
                    "affectations",
                    affectationRepository.findAll()
            );

            /*
             * Paramètres de recherche vides
             */
            model.addAttribute("q", null);
            model.addAttribute("debut", null);
            model.addAttribute("fin", null);

            return "affectations";
        }

        /*
         * Enregistrement
         */
        affectationRepository.save(form);

        /*
         * Retour vers la page
         */
        return "redirect:/affectations";
    }


    /**
     * =========================================================
     * SUPPRESSION
     *
     * URL :
     * POST /affectations/{id}/delete
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
                Model model
        ) {

        System.out.println(">>> EDIT affectation id = " + id);

        Map<String, Object> affectation =
                affectationRepository.findById(id);

        System.out.println(">>> affectation = " + affectation);

        if (affectation == null) {
                System.out.println(">>> AFFECTATION NULL");
                return "redirect:/affectations";
        }

        System.out.println(">>> clés = " + affectation.keySet());

        model.addAttribute("affectation", affectation);

        model.addAttribute(
                "agents",
                agentRepository.findAll()
        );

        return "edit_affectation";
        }
    /**
     * =========================================================
     * EXPORT EXCEL
     *
     * /affectations/export?debut=2026-09-01&fin=2026-09-30
     * =========================================================
     */

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
                affectationRepository.findBetween(
                        debut,
                        fin
                );

        byte[] excel =
                excelExportService.export(
                        rows,
                        debut,
                        fin
                );

        String filename =
                "Pointage_"
                        + debut
                        + "_au_"
                        + fin
                        + ".xlsx";

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
                .contentLength(
                        excel.length
                )
                .body(resource);
    }
}