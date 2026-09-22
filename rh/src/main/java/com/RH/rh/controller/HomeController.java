package com.RH.rh.controller;

import com.RH.rh.model.Pointage;
import com.RH.rh.repository.PointageRepository;
import com.RH.rh.service.PointagePdfService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin,
            @RequestParam(required = false) String telephone,
            @RequestParam(required = false) String equipe,
            Authentication authentication,
            Model model
    ) {

        String aujourdHuiString = LocalDate.now().toString();

        if (dateDebut == null || dateDebut.isBlank()) dateDebut = aujourdHuiString;
        if (dateFin == null || dateFin.isBlank()) dateFin = aujourdHuiString;
        if (telephone == null) telephone = "";
        if (equipe == null) equipe = "";

       // String username = authentication.getName();

        List<Pointage> rows =
                pointageRepository.rechercher(dateDebut, dateFin, telephone, equipe);

        // Regroupement par équipe (ordre alphabétique)
        Map<String, List<Pointage>> groupes = rows.stream()
                .collect(Collectors.groupingBy(
                        p -> (p.getEquipe() == null || p.getEquipe().isBlank())
                                ? "Sans équipe" : p.getEquipe(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        model.addAttribute("rows", rows);
        model.addAttribute("groupes", groupes);
        List<String> equipes = pointageRepository.findEquipes();
        System.out.println("EQUIPES = " + equipes);
        model.addAttribute("equipes", equipes);
        model.addAttribute("dateDebut", dateDebut);
        model.addAttribute("dateFin", dateFin);
        model.addAttribute("telephone", telephone);
        model.addAttribute("equipe", equipe);

        boolean rechercheActive =
                !dateDebut.equals(aujourdHuiString)
                || !dateFin.equals(aujourdHuiString)
                || !telephone.isBlank()
                || !equipe.isBlank();

        model.addAttribute("rechercheActive", rechercheActive);

        return "index";
    }

    // =====================================================
    // EXPORT PDF
    // =====================================================

    @GetMapping("/home/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin,
            @RequestParam(required = false) String telephone,
            @RequestParam(required = false) String equipe,
            Authentication authentication
    ) {

        String aujourdHuiString = LocalDate.now().toString();

        if (dateDebut == null || dateDebut.isBlank()) dateDebut = aujourdHuiString;
        if (dateFin == null || dateFin.isBlank()) dateFin = aujourdHuiString;
        if (telephone == null) telephone = "";
        if (equipe == null) equipe = "";

        //String username = authentication.getName();

        List<Pointage> rows =
                pointageRepository.rechercher(dateDebut, dateFin, telephone, equipe);

        byte[] pdf =
                pointagePdfService.generatePdf(rows, dateDebut, dateFin, telephone);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=pointages.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}