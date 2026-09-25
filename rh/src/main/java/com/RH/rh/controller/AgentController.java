package com.RH.rh.controller;

import com.RH.rh.model.Agent;
import com.RH.rh.repository.AgentRepository;

import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
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

            BindingResult result,

            Model model

    ) {

        if (result.hasErrors()) {

            model.addAttribute(
                    "agents",
                    agentRepository.search(null, null, null, null)
            );

            return "agents";
        }

        // 1) Vérification explicite AVANT l'insertion.
        //    On ne se fie pas uniquement à la contrainte UNIQUE + exception,
        //    car le driver libSQL/Turso n'est pas forcément reconnu par
        //    SQLErrorCodesFactory : DuplicateKeyException risquerait de ne
        //    jamais être levée alors qu'une SQLException générique, oui.
        if (agentRepository.existsByMatricule(agent.getMatricule())) {

            model.addAttribute(
                    "erreur",
                    "Ce matricule existe déjà : " + agent.getMatricule()
            );

            model.addAttribute(
                    "agent",
                    agent
            );

            model.addAttribute(
                    "agents",
                    agentRepository.search(null, null, null, null)
            );

            return "agents";
        }

        try {

            agentRepository.save(agent);

        } catch (DataIntegrityViolationException ex) {

            // 2) Filet de sécurité en cas d'accès concurrent (deux insertions
            //    simultanées passées entre la vérification et l'insertion).
            //    Nécessite une contrainte UNIQUE sur la colonne matricule.

            model.addAttribute(
                    "erreur",
                    "Ce matricule existe déjà : " + agent.getMatricule()
            );

            model.addAttribute(
                    "agent",
                    agent
            );

            model.addAttribute(
                    "agents",
                    agentRepository.search(null, null, null, null)
            );

            return "agents";
        }


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

            BindingResult result,

            Model model

    ) {

        if (result.hasErrors()) {

            return "edit_agents";
        }

        agent.setId(id);

        // Vérification explicite : un AUTRE agent porte-t-il déjà ce matricule ?
        if (agentRepository.existsByMatriculeAndIdNot(agent.getMatricule(), id)) {

            result.rejectValue(
                    "matricule",
                    "duplicate",
                    "Ce matricule existe déjà."
            );

            return "edit_agents";
        }

        try {

            agentRepository.update(agent);

        } catch (DataIntegrityViolationException ex) {

            result.rejectValue(
                    "matricule",
                    "duplicate",
                    "Ce matricule existe déjà."
            );

            return "edit_agents";
        }

        return "redirect:/agents";
    }
}