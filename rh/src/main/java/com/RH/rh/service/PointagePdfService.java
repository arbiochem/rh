package com.RH.rh.service;

import com.RH.rh.model.Pointage;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;

import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

import java.text.SimpleDateFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.Date;
import java.util.List;

@Service
public class PointagePdfService {

    public byte[] generatePdf(
            List<Pointage> rows,
            String dateDebut,
            String dateFin,
            String telephone
    ) {

        ByteArrayOutputStream outputStream =
                new ByteArrayOutputStream();

        Document document =
                new Document(PageSize.A4.rotate());

        try {

            PdfWriter.getInstance(
                    document,
                    outputStream
            );

            document.open();

            // =====================================================
            // TITRE
            // =====================================================

            Font titreFont =
                    new Font(
                            Font.HELVETICA,
                            18,
                            Font.BOLD
                    );

            Paragraph titre =
                    new Paragraph(
                            "RAPPORT DES POINTAGES",
                            titreFont
                    );

            titre.setAlignment(
                    Element.ALIGN_CENTER
            );

            titre.setSpacingAfter(10);

            document.add(titre);


            // =====================================================
            // INFORMATIONS
            // =====================================================

            Font infoFont =
                    new Font(
                            Font.HELVETICA,
                            10
                    );

            String filtreTelephone =
                    (telephone == null || telephone.isBlank())
                            ? "Tous les téléphones"
                            : telephone;

            Paragraph informations =
                    new Paragraph(
                            "Période : "
                                    + valeur(dateDebut)
                                    + " au "
                                    + valeur(dateFin)
                                    + "    |    Téléphone : "
                                    + filtreTelephone,
                            infoFont
                    );

            informations.setSpacingAfter(15);

            document.add(informations);


            // =====================================================
            // TABLEAU
            // =====================================================

            PdfPTable table =
                    new PdfPTable(6);

            table.setWidthPercentage(100);

            table.setWidths(
                    new float[]{
                            15f,
                            25f,
                            25f,
                            12f,
                            10f,
                            13f
                    }
            );


            // =====================================================
            // EN-TÊTE DU TABLEAU
            // =====================================================

            Font headerFont =
                    new Font(
                            Font.HELVETICA,
                            10,
                            Font.BOLD
                    );

            String[] headers = {

                    "Téléphone",
                    "Agent",
                    "Site / Lieu",
                    "Date",
                    "Heure",
                    "Type"

            };


            for (String header : headers) {

                PdfPCell cell =
                        new PdfPCell(
                                new Phrase(
                                        header,
                                        headerFont
                                )
                        );

                cell.setHorizontalAlignment(
                        Element.ALIGN_CENTER
                );

                cell.setVerticalAlignment(
                        Element.ALIGN_MIDDLE
                );

                cell.setPadding(6);

                table.addCell(cell);
            }


            // =====================================================
            // CORPS DU TABLEAU
            // =====================================================

            Font bodyFont =
                    new Font(
                            Font.HELVETICA,
                            9
                    );


            if (rows != null && !rows.isEmpty()) {

                for (Pointage p : rows) {


                    // =================================================
                    // TÉLÉPHONE
                    // =================================================

                    PdfPCell telephoneCell =
                            new PdfPCell(
                                    new Phrase(
                                            valeur(p.getTelephone()),
                                            bodyFont
                                    )
                            );

                    telephoneCell.setVerticalAlignment(
                            Element.ALIGN_MIDDLE
                    );

                    table.addCell(
                            telephoneCell
                    );


                    // =================================================
                    // AGENT
                    // =================================================

                    PdfPCell agentCell =
                            new PdfPCell(
                                    new Phrase(
                                            valeur(p.getAgent()),
                                            bodyFont
                                    )
                            );

                    agentCell.setVerticalAlignment(
                            Element.ALIGN_MIDDLE
                    );

                    table.addCell(
                            agentCell
                    );


                    // =================================================
                    // SITE
                    // =================================================

                    PdfPCell siteCell =
                            new PdfPCell(
                                    new Phrase(
                                            valeur(p.getSite()),
                                            bodyFont
                                    )
                            );

                    siteCell.setVerticalAlignment(
                            Element.ALIGN_MIDDLE
                    );

                    table.addCell(
                            siteCell
                    );


                    // =================================================
                    // DATE
                    // =================================================

                    String date =
                            formaterDate(
                                    p.getDate()
                            );

                    PdfPCell dateCell =
                            new PdfPCell(
                                    new Phrase(
                                            date,
                                            bodyFont
                                    )
                            );

                    dateCell.setHorizontalAlignment(
                            Element.ALIGN_CENTER
                    );

                    dateCell.setVerticalAlignment(
                            Element.ALIGN_MIDDLE
                    );

                    table.addCell(
                            dateCell
                    );


                    // =================================================
                    // HEURE
                    // =================================================

                    PdfPCell heureCell =
                            new PdfPCell(
                                    new Phrase(
                                            valeur(p.getHeure()),
                                            bodyFont
                                    )
                            );

                    heureCell.setHorizontalAlignment(
                            Element.ALIGN_CENTER
                    );

                    heureCell.setVerticalAlignment(
                            Element.ALIGN_MIDDLE
                    );

                    table.addCell(
                            heureCell
                    );


                    // =================================================
                    // TYPE
                    // =================================================

                    PdfPCell typeCell =
                            new PdfPCell(
                                    new Phrase(
                                            valeur(p.getType()),
                                            bodyFont
                                    )
                            );

                    typeCell.setHorizontalAlignment(
                            Element.ALIGN_CENTER
                    );

                    typeCell.setVerticalAlignment(
                            Element.ALIGN_MIDDLE
                    );

                    table.addCell(
                            typeCell
                    );
                }

            } else {

                // =====================================================
                // AUCUN RÉSULTAT
                // =====================================================

                PdfPCell emptyCell =
                        new PdfPCell(
                                new Phrase(
                                        "Aucun pointage sur cette période.",
                                        bodyFont
                                )
                        );

                emptyCell.setColspan(6);

                emptyCell.setHorizontalAlignment(
                        Element.ALIGN_CENTER
                );

                emptyCell.setVerticalAlignment(
                        Element.ALIGN_MIDDLE
                );

                emptyCell.setPadding(10);

                table.addCell(
                        emptyCell
                );
            }


            // =====================================================
            // AJOUT DU TABLEAU
            // =====================================================

            document.add(table);


            // =====================================================
            // TOTAL
            // =====================================================

            int totalPointages =
                    rows == null
                            ? 0
                            : rows.size();

            Paragraph total =
                    new Paragraph(
                            "Nombre de pointages : "
                                    + totalPointages,
                            infoFont
                    );

            total.setSpacingBefore(10);

            document.add(total);

        } catch (Exception e) {

            throw new RuntimeException(
                    "Erreur lors de la génération du PDF",
                    e
            );

        } finally {

            if (document.isOpen()) {
                document.close();
            }
        }

        return outputStream.toByteArray();
    }


    // =============================================================
    // FORMATAGE ROBUSTE DE LA DATE
    // =============================================================

    private String formaterDate(Object dateObject) {

        if (dateObject == null) {
            return "";
        }


        // =========================================================
        // java.util.Date
        // =========================================================

        if (dateObject instanceof Date) {

            SimpleDateFormat formatter =
                    new SimpleDateFormat(
                            "dd/MM/yyyy"
                    );

            return formatter.format(
                    (Date) dateObject
            );
        }


        // =========================================================
        // java.sql.Date
        // =========================================================

        if (dateObject instanceof java.sql.Date) {

            SimpleDateFormat formatter =
                    new SimpleDateFormat(
                            "dd/MM/yyyy"
                    );

            return formatter.format(
                    (java.sql.Date) dateObject
            );
        }


        // =========================================================
        // LocalDate
        // =========================================================

        if (dateObject instanceof LocalDate) {

            LocalDate date =
                    (LocalDate) dateObject;

            return String.format(
                    "%02d/%02d/%04d",
                    date.getDayOfMonth(),
                    date.getMonthValue(),
                    date.getYear()
            );
        }


        // =========================================================
        // LocalDateTime
        // =========================================================

        if (dateObject instanceof LocalDateTime) {

            LocalDateTime date =
                    (LocalDateTime) dateObject;

            return String.format(
                    "%02d/%02d/%04d",
                    date.getDayOfMonth(),
                    date.getMonthValue(),
                    date.getYear()
            );
        }


        // =========================================================
        // STRING
        // =========================================================

        if (dateObject instanceof String) {

            String date =
                    (String) dateObject;

            if (date.isBlank()) {
                return "";
            }

            /*
             * Si la base contient déjà :
             *
             * 2026-09-10
             *
             */

            try {

                LocalDate localDate =
                        LocalDate.parse(date);

                return String.format(
                        "%02d/%02d/%04d",
                        localDate.getDayOfMonth(),
                        localDate.getMonthValue(),
                        localDate.getYear()
                );

            } catch (Exception ignored) {
                // On essaie d'autres formats
            }


            /*
             * Format :
             *
             * 2026/09/10
             */

            try {

                SimpleDateFormat inputFormat =
                        new SimpleDateFormat(
                                "yyyy/MM/dd"
                        );

                SimpleDateFormat outputFormat =
                        new SimpleDateFormat(
                                "dd/MM/yyyy"
                        );

                Date parsed =
                        inputFormat.parse(date);

                return outputFormat.format(parsed);

            } catch (Exception ignored) {
                // On retourne la valeur originale
            }


            return date;
        }


        // =========================================================
        // AUTRE TYPE
        // =========================================================

        return dateObject.toString();
    }


    // =============================================================
    // VALEUR STRING SANS NULL
    // =============================================================

    private String valeur(String value) {

        return value == null
                ? ""
                : value;
    }
}