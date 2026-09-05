package com.RH.rh.controller;

import com.RH.rh.model.Site;
import com.RH.rh.repository.SiteRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/sites")
public class SiteController {

    private final SiteRepository siteRepository;

    public SiteController(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    @GetMapping
    public String liste(Model model) {
        model.addAttribute("sites", siteRepository.findAll());
        return "sites/list";
    }

    @GetMapping("/nouveau")
    public String formulaireNouveau(Model model) {
        model.addAttribute("site", new Site());
        return "sites/form";
    }

    @GetMapping("/{id}/modifier")
    public String formulaireModifier(@PathVariable Long id, Model model) {
        Site site = siteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Site introuvable : " + id));
        model.addAttribute("site", site);
        return "sites/form";
    }

    @PostMapping("/enregistrer")
    public String enregistrer(@Valid @ModelAttribute("site") Site site, BindingResult result) {
        if (result.hasErrors()) {
            return "sites/form";
        }
        siteRepository.save(site);
        return "redirect:/sites";
    }

    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id) {
        siteRepository.deleteById(id);
        return "redirect:/sites";
    }
}
