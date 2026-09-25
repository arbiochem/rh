package com.RH.rh.controller;

import com.RH.rh.model.Pointage;
import com.RH.rh.dto.PointageAffichage;
import com.RH.rh.repository.PointageRepository;
import com.RH.rh.repository.UtilisateurRepository;
import com.RH.rh.service.PointagePdfService;
import com.RH.rh.service.PointageSyncService;
import com.RH.rh.service.PointageSyncService.ResultatSync;
import com.RH.rh.dto.PointageDisplayUtils;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

    private final PointageRepository pointageRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final PointagePdfService pointagePdfService;
    private final PointageSyncService pointageSyncService;

    public HomeController(
            PointageRepository pointageRepository,
            UtilisateurRepository utilisateurRepository,
            PointagePdfService pointagePdfService,
            PointageSyncService pointageSyncService
    ) {
        this.pointageRepository = pointageRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.pointagePdfService = pointagePdfService;
        this.pointageSyncService = pointageSyncService;
    }

    /**
     * Détermine si l'utilisateur connecté est admin, et si non, retrouve son équipe
     * pour la forcer dans les recherches (sécurité côté serveur).
     */
    private boolean[] resoudreAccesAdmin(Authentication authentication) {
        // placeholder non utilisé, voir méthode ci-dessous
        return null;
    }

    private boolean estAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private String equipeDeLUtilisateur(Authentication authentication) {
        Optional<Long> agentId = utilisateurRepository.findAgentIdByUsername(authentication.getName());
        if (agentId.isEmpty()) {
            return null; // utilisateur sans agent lié : ne verra aucun résultat
        }
        return pointageRepository.findEquipeParAgentId(agentId.get());
    }

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

        boolean isAdmin = estAdmin(authentication);
        String equipeUtilisateur = isAdmin ? null : equipeDeLUtilisateur(authentication);

        // 1) on rapatrie d'abord les pointages de Turso vers SQL Server
        //    (puis ils sont supprimés de Turso), 2) on lit SQL Server.
        pointageSyncService.synchroniser();

        List<Pointage> rows =
                pointageRepository.rechercher(dateDebut, dateFin, telephone, equipe, isAdmin, equipeUtilisateur);

        Map<String, List<Pointage>> groupesBruts = rows.stream()
                .collect(Collectors.groupingBy(
                        p -> (p.getEquipe() == null || p.getEquipe().isBlank())
                                ? "Sans équipe" : p.getEquipe(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        Map<String, List<PointageAffichage>> groupes = new LinkedHashMap<>();
        for (Map.Entry<String, List<Pointage>> entry : groupesBruts.entrySet()) {
            groupes.put(entry.getKey(), PointageDisplayUtils.grouperParLieu(entry.getValue()));
        }

        model.addAttribute("rows", rows);
        model.addAttribute("groupes", groupes);

        // Chefs d'équipe (role_equipe = 'CHEF'), indépendamment des
        // pointages du jour, pour l'affichage "dirigée par" côté vue.
        model.addAttribute("chefsParEquipe", pointageRepository.findChefsParEquipe());

        // Un non-admin ne voit que sa propre équipe dans le menu déroulant (ou rien,
        // le sélecteur pouvant même être caché côté vue grâce à isAdmin ci-dessous).
        List<String> equipes = isAdmin
                ? pointageRepository.findEquipes()
                : (equipeUtilisateur != null ? List.of(equipeUtilisateur) : List.of());
        model.addAttribute("equipes", equipes);

        model.addAttribute("dateDebut", dateDebut);
        model.addAttribute("dateFin", dateFin);
        model.addAttribute("telephone", telephone);
        model.addAttribute("equipe", isAdmin ? equipe : (equipeUtilisateur != null ? equipeUtilisateur : ""));
        model.addAttribute("isAdmin", isAdmin);

        boolean rechercheActive =
                !dateDebut.equals(aujourdHuiString)
                || !dateFin.equals(aujourdHuiString)
                || !telephone.isBlank()
                || !equipe.isBlank();

        model.addAttribute("rechercheActive", rechercheActive);

        return "index";
    }

    /**
     * Bouton « Actualiser » : lance la synchronisation Turso -> SQL Server
     * manuellement, puis revient sur /home en gardant les filtres.
     */
    @PostMapping("/home/synchroniser")
    public String synchroniserManuellement(
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin,
            @RequestParam(required = false) String telephone,
            @RequestParam(required = false) String equipe,
            RedirectAttributes redirect
    ) {

        ResultatSync r = pointageSyncService.synchroniserAvecResultat();

        if (r.enCours()) {
            redirect.addFlashAttribute("syncType", "warning");
            redirect.addFlashAttribute("syncMessage",
                    "Une synchronisation est déjà en cours, réessayez dans un instant.");
        } else if (r.erreur() != null) {
            redirect.addFlashAttribute("syncType", "danger");
            redirect.addFlashAttribute("syncMessage",
                    "Échec de la synchronisation (" + r.transferes()
                            + " pointage(s) transféré(s)) : " + r.erreur());
        } else if (r.transferes() == 0) {
            redirect.addFlashAttribute("syncType", "info");
            redirect.addFlashAttribute("syncMessage",
                    "Synchronisation terminée : aucun nouveau pointage.");
        } else {
            redirect.addFlashAttribute("syncType", "success");
            redirect.addFlashAttribute("syncMessage",
                    "Synchronisation terminée : " + r.transferes()
                            + " pointage(s) transféré(s).");
        }

        // On conserve les filtres de la recherche en cours
        if (dateDebut != null && !dateDebut.isBlank()) redirect.addAttribute("dateDebut", dateDebut);
        if (dateFin != null && !dateFin.isBlank()) redirect.addAttribute("dateFin", dateFin);
        if (telephone != null && !telephone.isBlank()) redirect.addAttribute("telephone", telephone);
        if (equipe != null && !equipe.isBlank()) redirect.addAttribute("equipe", equipe);

        return "redirect:/home";
    }

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

        boolean isAdmin = estAdmin(authentication);
        String equipeUtilisateur = isAdmin ? null : equipeDeLUtilisateur(authentication);

        // 1) on rapatrie d'abord les pointages de Turso vers SQL Server
        //    (puis ils sont supprimés de Turso), 2) on lit SQL Server.
        pointageSyncService.synchroniser();

        List<Pointage> rows =
                pointageRepository.rechercher(dateDebut, dateFin, telephone, equipe, isAdmin, equipeUtilisateur);

        byte[] pdf =
                pointagePdfService.generatePdf(rows, dateDebut, dateFin, telephone);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=pointages.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}