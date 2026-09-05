package com.RH.rh.controller;

import com.RH.rh.model.Affectation;
import com.RH.rh.repository.AffectationRepository;
import com.RH.rh.service.ExcelExportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
public class ExportController {

    private final AffectationRepository affectationRepository;
    private final ExcelExportService excelExportService;

    public ExportController(AffectationRepository affectationRepository, ExcelExportService excelExportService) {
        this.affectationRepository = affectationRepository;
        this.excelExportService = excelExportService;
    }

    @GetMapping("/export/excel")
    public ResponseEntity<ByteArrayResource> exporterExcel(
            @RequestParam(required = false) String debut,
            @RequestParam(required = false) String fin) throws IOException {

        LocalDate dateFin = (fin != null && !fin.isBlank()) ? LocalDate.parse(fin) : LocalDate.now();
        LocalDate dateDebut = (debut != null && !debut.isBlank()) ? LocalDate.parse(debut) : dateFin.minusDays(6);

        List<Affectation> affectations =
                affectationRepository.findByDateAffectationBetweenOrderByDateAffectationAscIdAsc(dateDebut, dateFin);

        byte[] fichier = excelExportService.exporterAffectations(affectations);

        String nomFichier = "affectations_" + dateDebut + "_au_" + dateFin + ".xlsx";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomFichier + "\"")
                .body(new ByteArrayResource(fichier));
    }
}
