package com.RH.rh.controller;

import com.RH.rh.model.Affectation;
import com.RH.rh.repository.AffectationRepository;
import com.RH.rh.repository.AgentRepository;
import com.RH.rh.repository.SiteRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    private final AffectationRepository affectationRepository;
    private final AgentRepository agentRepository;
    private final SiteRepository siteRepository;

    public DashboardController(AffectationRepository affectationRepository,
                                AgentRepository agentRepository,
                                SiteRepository siteRepository) {
        this.affectationRepository = affectationRepository;
        this.agentRepository = agentRepository;
        this.siteRepository = siteRepository;
    }

    @GetMapping("/dashboard")
    public String tableauDeBord(@RequestParam(required = false) String debut,
                                 @RequestParam(required = false) String fin,
                                 Model model) {

        LocalDate dateFin = (fin != null && !fin.isBlank()) ? LocalDate.parse(fin) : LocalDate.now();
        LocalDate dateDebut = (debut != null && !debut.isBlank()) ? LocalDate.parse(debut) : dateFin.minusDays(6);

        List<Affectation> affectations =
                affectationRepository.findByDateAffectationBetweenOrderByDateAffectationAscIdAsc(dateDebut, dateFin);

        Map<String, Long> parStatut = affectations.stream()
                .collect(Collectors.groupingBy(Affectation::getStatut, Collectors.counting()));

        Map<String, Long> parLieu = affectations.stream()
                .collect(Collectors.groupingBy(Affectation::getLieuAffiche, Collectors.counting()));

        Map<LocalDate, Long> parJour = affectations.stream()
                .collect(Collectors.groupingBy(Affectation::getDateAffectation, Collectors.counting()));

        model.addAttribute("affectations", affectations);
        model.addAttribute("dateDebut", dateDebut);
        model.addAttribute("dateFin", dateFin);
        model.addAttribute("totalAffectations", affectations.size());
        model.addAttribute("totalAgents", agentRepository.count());
        model.addAttribute("totalSites", siteRepository.count());
        model.addAttribute("parStatut", parStatut);
        model.addAttribute("parLieu", parLieu);
        model.addAttribute("parJour", parJour);

        return "dashboard";
    }
}
