package com.RH.rh.repository;

import com.RH.rh.model.Pointage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class PointageRepository {

    private final JdbcTemplate jdbcTemplate;

    public PointageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Pointage> rechercher(String dateDebut, String dateFin, String telephone) {

       String sql = """
        SELECT
            p.telephone AS telephone,
            ag.matricule || ' : ' || ag.nom || ' ' || ag.prenom AS agent,
            p.lieu AS site,
            p.date_heure,
            p.type
        FROM pointages AS p
        INNER JOIN agents AS ag
            ON ag.telephone = p.telephone
        WHERE replace(substr(p.date_heure, 1, 10), '/', '-')
              BETWEEN ? AND ?
        AND (? IS NULL OR ? = '' OR p.telephone = ?)
        ORDER BY p.date_heure ASC
        """;

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> {

                Pointage p = new Pointage();

                p.setTelephone(rs.getString("telephone"));
                p.setAgent(rs.getString("agent"));
                p.setSite(rs.getString("site"));

                String dateHeure = rs.getString("date_heure");

                if (dateHeure != null && dateHeure.length() >= 19) {
                    String dateHeures = rs.getString("date_heure");

                    if (dateHeures != null && dateHeures.length() >= 10) {
                        p.setDate(dateHeures.substring(0, 10));
                    } else {
                        p.setDate("");
                    }

                    p.setHeure(
                            dateHeures.substring(11, 19)
                    );
                }

                p.setType(rs.getString("type"));

                return p;
            },
            dateDebut,
            dateFin,
            telephone,
            telephone,
            telephone
        );
    }

    public List<Pointage> rechercherDuJour(String date,String telephone) {
        return rechercher(date, date, telephone);
    }
}

