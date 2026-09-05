package com.RH.rh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application RH - gestion des agents, affectations journalières,
 * tableau de bord et export Excel, adossée à une base Turso (libSQL).
 */
@SpringBootApplication
public class RhApplication {

    public static void main(String[] args) {
        System.out.println(">>> Demarrage de l'application RH...");
        SpringApplication.run(RhApplication.class, args);
        System.out.println(">>> Application RH demarree avec succes !");
    }
}
