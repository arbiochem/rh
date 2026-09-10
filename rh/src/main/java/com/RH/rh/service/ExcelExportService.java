package com.RH.rh.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class ExcelExportService {


    public byte[] export(
            List<Map<String, Object>> rows,
            LocalDate debut,
            LocalDate fin
    ) throws IOException {


        try (
                Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream outputStream =
                        new ByteArrayOutputStream()
        ) {


            Sheet sheet =
                    workbook.createSheet("Pointage");


            // =====================================================
            // STYLE TITRE
            // =====================================================

            CellStyle titleStyle =
                    workbook.createCellStyle();

            Font titleFont =
                    workbook.createFont();

            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);

            titleStyle.setFont(titleFont);


            // =====================================================
            // STYLE ENTETE
            // =====================================================

            CellStyle headerStyle =
                    workbook.createCellStyle();

            Font headerFont =
                    workbook.createFont();

            headerFont.setBold(true);

            headerStyle.setFont(headerFont);


            // =====================================================
            // TITRE
            // =====================================================

            Row titleRow =
                    sheet.createRow(0);

            Cell titleCell =
                    titleRow.createCell(0);

            titleCell.setCellValue(
                    "POINTAGE RH"
            );

            titleCell.setCellStyle(
                    titleStyle
            );


            Row periodRow =
                    sheet.createRow(1);

            periodRow.createCell(0)
                    .setCellValue(
                            "Période : "
                                    + debut
                                    + " au "
                                    + fin
                    );


            // =====================================================
            // ENTETES
            // =====================================================

            Row headerRow =
                    sheet.createRow(3);


            String[] headers = {

                    "Date",

                    "Matricule",

                    "Agent",

                    "Site / Lieu",

                    "Adresse",

                    "Heure début",

                    "Heure fin",

                    "Observation"

            };


            for (
                    int i = 0;
                    i < headers.length;
                    i++
            ) {

                Cell cell =
                        headerRow.createCell(i);

                cell.setCellValue(
                        headers[i]
                );

                cell.setCellStyle(
                        headerStyle
                );
            }


            // =====================================================
            // DONNEES
            // =====================================================

            int rowIndex = 4;


            for (
                    Map<String, Object> data :
                    rows
            ) {

                Row row =
                        sheet.createRow(
                                rowIndex++
                        );


                row.createCell(0)
                        .setCellValue(
                                value(
                                        data,
                                        "date_affectation"
                                )
                        );


                row.createCell(1)
                        .setCellValue(
                                value(
                                        data,
                                        "matricule"
                                )
                        );


                row.createCell(2)
                        .setCellValue(
                                value(
                                        data,
                                        "agent"
                                )
                        );


                row.createCell(3)
                        .setCellValue(
                                value(
                                        data,
                                        "site"
                                )
                        );


                row.createCell(4)
                        .setCellValue(
                                value(
                                        data,
                                        "adresse"
                                )
                        );


                row.createCell(5)
                        .setCellValue(
                                value(
                                        data,
                                        "heure_debut"
                                )
                        );


                row.createCell(6)
                        .setCellValue(
                                value(
                                        data,
                                        "heure_fin"
                                )
                        );


                row.createCell(7)
                        .setCellValue(
                                value(
                                        data,
                                        "observation"
                                )
                        );
            }


            // =====================================================
            // LARGEUR DES COLONNES
            // =====================================================

            for (int i = 0; i < headers.length; i++) {

                sheet.autoSizeColumn(i);

            }


            workbook.write(
                    outputStream
            );


            return outputStream.toByteArray();
        }
    }


    private String value(
            Map<String, Object> row,
            String key
    ) {

        Object value =
                row.get(key);

        return value == null
                ? ""
                : value.toString();
    }
}