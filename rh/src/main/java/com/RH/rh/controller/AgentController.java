package com.RH.rh.controller;

import com.RH.rh.model.Agent;
import com.RH.rh.repository.AgentRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/agents")
public class AgentController {

    private final AgentRepository agentRepository;

    public AgentController(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    @GetMapping
    public String liste(Model model) {
        model.addAttribute("agents", agentRepository.findAll());
        return "agents/list";
    }

    @GetMapping("/nouveau")
    public String formulaireNouveau(Model model) {
        model.addAttribute("agent", new Agent());
        return "agents/form";
    }

    @GetMapping("/{id}/modifier")
    public String formulaireModifier(@PathVariable Long id, Model model) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent introuvable : " + id));
        model.addAttribute("agent", agent);
        return "agents/form";
    }

    @PostMapping("/enregistrer")
    public String enregistrer(@Valid @ModelAttribute("agent") Agent agent, BindingResult result) {
        if (result.hasErrors()) {
            return "agents/form";
        }
        agentRepository.save(agent);
        return "redirect:/agents";
    }

    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id) {
        agentRepository.deleteById(id);
        return "redirect:/agents";
    }
}
