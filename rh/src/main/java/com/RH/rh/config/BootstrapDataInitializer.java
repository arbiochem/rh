package com.RH.rh.config;

import com.RH.rh.model.Utilisateur;
import com.RH.rh.repository.UtilisateurRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class BootstrapDataInitializer {

    @Bean
    public CommandLineRunner creerAdminParDefaut(UtilisateurRepository utilisateurRepository,
                                                   PasswordEncoder passwordEncoder) {
        return args -> {
            if (utilisateurRepository.findByUsername("admin").isEmpty()) {
                Utilisateur admin = new Utilisateur();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setRole("ADMIN");
                admin.setActif(true);
                utilisateurRepository.save(admin);
                System.out.println(">>> Compte admin par defaut cree (admin / admin123). Changez ce mot de passe rapidement !");
            } else {
                System.out.println(">>> Compte admin deja present, aucune creation necessaire.");
            }
        };
    }
}