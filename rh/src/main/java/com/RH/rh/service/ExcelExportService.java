package com.RH.rh.service;

import com.RH.rh.model.Affectation;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ExcelExportService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String[] EN_TETES = {
            "Date", "Matricule", "Agent", "Poste", "Lieu / Site",
            "Heure debut", "Heure fin", "Statut", "Commentaire"
    };

    public byte[] exporterAffectations(List<Affectation> affectations) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Affectations");

            CellStyle styleEntete = creerStyleEntete(workbook);
            CellStyle styleDate = workbook.createCellStyle();
            styleDate.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));

            Row entete = sheet.createRow(0);
            for (int i = 0; i < EN_TETES.length; i++) {
                Cell cell = entete.createCell(i);
                cell.setCellValue(EN_TETES[i]);
                cell.setCellStyle(styleEntete);
            }

            int ligne = 1;
            for (Affectation a : affectations) {
                Row row = sheet.createRow(ligne++);
                row.createCell(0).setCellValue(
                        a.getDateAffectation() != null ? a.getDateAffectation().format(FMT) : "");
                row.createCell(1).setCellValue(nvl(a.getAgent() != null ? a.getAgent().getMatricule() : null));
                row.createCell(2).setCellValue(a.getAgent() != null ? a.getAgent().getNomComplet() : "");
                row.createCell(3).setCellValue(nvl(a.getAgent() != null ? a.getAgent().getPoste() : null));
                row.createCell(4).setCellValue(a.getLieuAffiche());
                row.createCell(5).setCellValue(nvl(a.getHeureDebut()));
                row.createCell(6).setCellValue(nvl(a.getHeureFin()));
                row.createCell(7).setCellValue(nvl(a.getStatut()));
                row.createCell(8).setCellValue(nvl(a.getCommentaire()));
            }

            for (int i = 0; i < EN_TETES.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private CellStyle creerStyleEntete(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }
}
