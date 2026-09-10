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


    public AgentController(
            AgentRepository agentRepository
    ) {

        this.agentRepository =
                agentRepository;
    }


    @GetMapping
    public String index(    
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "prenom", required = false) String prenom,
            @RequestParam(value = "matricule", required = false) String matricule,
            @RequestParam(value = "nom", required = false) String nom,
            Model model) {

        model.addAttribute("agent", new Agent());
        model.addAttribute("q", q);
        model.addAttribute("prenom", prenom);
        model.addAttribute("matricule", matricule);
        model.addAttribute("nom", nom);

        model.addAttribute(
                "agents",
                agentRepository.search(q, matricule, nom, prenom)
        );

        return "agents";
    }


    @PostMapping
    public String save(

            @Valid
            @ModelAttribute("agent")
            Agent agent,

            BindingResult result

    ) {

        if (result.hasErrors()) {

            return "agents";
        }


        agentRepository.save(agent);


        return "redirect:/agents";
    }


    @PostMapping("/delete/{id}")
    public String delete(
            @PathVariable Long id
    ) {

        agentRepository.delete(id);


        return "redirect:/agents";
    }

    @GetMapping("/edit/{id}")
    public String edit_agents(
            @PathVariable Long id,
            Model model
    ) {

        model.addAttribute(
                "agent",
                agentRepository.findById(id)
        );

        return "edit_agents";
    }


    @PostMapping("/edit/{id}")
    public String update(

            @PathVariable Long id,

            @Valid
            @ModelAttribute("agent")
            Agent agent,

            BindingResult result

    ) {

        if (result.hasErrors()) {

            return "edit_agents";
        }

        agent.setId(id);
        agentRepository.update(agent);

        return "redirect:/agents";
    }
}