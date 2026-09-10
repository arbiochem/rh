package com.RH.rh.controller;

import com.RH.rh.model.Pointage;
import com.RH.rh.repository.PointageRepository;
import com.RH.rh.service.PointagePdfService;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    private final PointageRepository pointageRepository;

    private final PointagePdfService pointagePdfService;


    public HomeController(
            PointageRepository pointageRepository,
            PointagePdfService pointagePdfService
    ) {

        this.pointageRepository = pointageRepository;

        this.pointagePdfService = pointagePdfService;
    }


    // =====================================================
    // PAGE HOME
    // =====================================================

        @GetMapping("/home")
        public String home(

                @RequestParam(required = false)
                String dateDebut,

                @RequestParam(required = false)
                String dateFin,

                @RequestParam(required = false)
                String telephone,

                Model model
        ) {

        LocalDate aujourdHui = LocalDate.now();

        String aujourdHuiString = aujourdHui.toString();

        // Dates par défaut
        if (dateDebut == null || dateDebut.isBlank()) {
                dateDebut = aujourdHuiString;
        }

        if (dateFin == null || dateFin.isBlank()) {
                dateFin = aujourdHuiString;
        }

        // IMPORTANT :
        // Ne jamais envoyer null à JdbcTemplate
        if (telephone == null) {
                telephone = "";
        }

        // Recherche
        List<Pointage> rows =
                pointageRepository.rechercher(
                        dateDebut,
                        dateFin,
                        telephone
                );

        // Données pour la page
        model.addAttribute("rows", rows);
        model.addAttribute("dateDebut", dateDebut);
        model.addAttribute("dateFin", dateFin);
        model.addAttribute("telephone", telephone);

        boolean rechercheActive =
                !dateDebut.equals(aujourdHuiString)
                || !dateFin.equals(aujourdHuiString)
                || !telephone.isBlank();

        model.addAttribute(
                "rechercheActive",
                rechercheActive
        );

        return "index";
        }

    // =====================================================
    // EXPORT PDF
    // =====================================================

    @GetMapping("/home/pdf")
    public ResponseEntity<byte[]> exportPdf(

            @RequestParam(required = false)
            String dateDebut,

            @RequestParam(required = false)
            String dateFin,

            @RequestParam(required = false)
            String telephone

    ) {

        LocalDate aujourdHui =
                LocalDate.now();


        if (dateDebut == null ||
                dateDebut.isBlank()) {

            dateDebut =
                    aujourdHui.toString();
        }


        if (dateFin == null ||
                dateFin.isBlank()) {

            dateFin =
                    aujourdHui.toString();
        }


        List<Pointage> rows =
                pointageRepository.rechercher(
                        dateDebut,
                        dateFin,
                        telephone
                );


        byte[] pdf =
                pointagePdfService.generatePdf(
                        rows,
                        dateDebut,
                        dateFin,
                        telephone
                );


        return ResponseEntity.ok()

                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=pointages.pdf"
                )

                .contentType(
                        MediaType.APPLICATION_PDF
                )

                .body(pdf);
    }
}