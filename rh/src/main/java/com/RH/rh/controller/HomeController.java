package com.RH.rh.controller;

import com.RH.rh.repository.AffectationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

@Controller
public class HomeController {

    private final AffectationRepository affectationRepository;


    public HomeController(
            AffectationRepository affectationRepository
    ) {

        this.affectationRepository =
                affectationRepository;
    }


    @GetMapping("/home")
    public String index(Model model) {

        LocalDate today =
                LocalDate.now();


        model.addAttribute(
                "today",
                today
        );


        model.addAttribute(
                "rows",
                affectationRepository
                        .findByDate(today)
        );


        return "index";
    }
}