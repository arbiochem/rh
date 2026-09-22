package com.RH.rh.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.RH.rh.model.Utilisateur;
import com.RH.rh.repository.UtilisateurRepository;

import java.util.List;

@Service
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    public UtilisateurService(UtilisateurRepository utilisateurRepository, PasswordEncoder passwordEncoder) {
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Utilisateur> listerUtilisateurs() {
        return utilisateurRepository.findAll();
    }

    public Utilisateur obtenirUtilisateur(Long id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + id));
    }

    public Utilisateur creerUtilisateur(String username, String password, String role, Long agentId) {
        if (utilisateurRepository.existsByUsername(username)) {
            throw new IllegalStateException("Ce nom d'utilisateur existe déjà.");
        }
        if (agentId != null && utilisateurRepository.agentDejaLie(agentId)) {
            throw new IllegalStateException("Cet agent est déjà lié à un compte utilisateur.");
        }
        Utilisateur u = new Utilisateur();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(password));
        u.setRole(role);
        u.setAgentId(agentId);
        return utilisateurRepository.save(u);
    }

    public void modifierRole(Long id, String role) {
        utilisateurRepository.update(id, role);
    }

    public void changerMotDePasse(Long id, String nouveauMotDePasse) {
        String hash = passwordEncoder.encode(nouveauMotDePasse);
        utilisateurRepository.changerMotDePasse(id, hash);
    }

    public void desactiverUtilisateur(Long id) {
        utilisateurRepository.desactiver(id);
    }

    public void reactiverUtilisateur(Long id) {
        utilisateurRepository.reactiver(id);
    }
}