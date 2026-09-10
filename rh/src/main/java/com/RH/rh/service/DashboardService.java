
package com.RH.rh.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.RH.rh.dto.PointageJourDTO;

@Service
public class DashboardService {

    public List<PointageJourDTO> getRosterDuJour() {
        return new ArrayList<>();
    }

    public List<PointageJourDTO> getRosterParPlage(
            String dateDebut,
            String dateFin) {

        return new ArrayList<>();
    }
}

