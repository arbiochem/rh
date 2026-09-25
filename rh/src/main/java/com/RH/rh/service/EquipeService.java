package com.RH.rh.service;

import org.springframework.stereotype.Service;

import com.RH.rh.model.Equipe;
import com.RH.rh.model.MembreEquipe;
import com.RH.rh.repository.EquipeRepository;
import com.RH.rh.repository.MembreEquipeRepository;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class EquipeService {

    private final EquipeRepository equipeRepository;

    private final MembreEquipeRepository membreRepository;

    public EquipeService(
            EquipeRepository equipeRepository,
            MembreEquipeRepository membreRepository) {

        this.equipeRepository = equipeRepository;
        this.membreRepository = membreRepository;
    }

    public List<Equipe> listerEquipes() {
        return equipeRepository.findAll();
    }

    public Equipe obtenirEquipe(Long id) {

        Equipe equipe = equipeRepository.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Équipe introuvable : " + id
                        )
                );

        equipe.setMembres(
                membreRepository.findByEquipeId(id)
        );

        return equipe;
    }

    public Equipe creerEquipe(
            String nom,
            String description) {

        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException(
                    "Le nom de l'équipe est obligatoire."
            );
        }

        if (equipeRepository.existsByNom(nom)) {
            throw new IllegalStateException(
                    "Une équipe avec ce nom existe déjà."
            );
        }

        return equipeRepository.save(
                new Equipe(
                        nom.trim(),
                        description
                )
        );
    }

    public void reactiverEquipe(Long id) {
        equipeRepository.reactiver(id);
    }

    public List<Equipe> listerEquipesInactives() {
        return equipeRepository.findAllInactives();
    }

    public void modifierEquipe(
            Long id,
            String nom,
            String description) {

        obtenirEquipe(id);

        equipeRepository.update(
                id,
                nom,
                description
        );
    }

    public void supprimerEquipe(
            Long id,
            boolean suppressionDefinitive) {

        obtenirEquipe(id);

        if (suppressionDefinitive) {
            equipeRepository.deleteHard(id);
        } else {
            equipeRepository.desactiver(id);
        }
    }

    /**
     * Ajoute un agent à une équipe.
     *
     * Bloque le doublon : si l'agent est déjà membre de cette équipe
     * (quel que soit son rôle actuel), l'ajout est refusé plutôt que
     * de créer une seconde ligne membres_equipe pour le même couple
     * (équipe, agent).
     */
    public MembreEquipe ajouterMembre(
            Long equipeId,
            Long agentId,
            MembreEquipe.RoleEquipe role) {

        obtenirEquipe(equipeId);

        if (membreRepository.existeMembre(equipeId, agentId)) {
            throw new IllegalStateException(
                    "Cet agent est déjà membre de cette équipe."
            );
        }

        return membreRepository.ajouterMembre(
                equipeId,
                agentId,
                role != null
                        ? role
                        : MembreEquipe.RoleEquipe.MEMBRE
        );
    }

    /**
     * Retire un agent d'une équipe.
     */
    public void retirerMembre(
            Long equipeId,
            Long agentId) {

        int lignes =
                membreRepository.retirerMembre(
                        equipeId,
                        agentId
                );

        if (lignes == 0) {
            throw new NoSuchElementException(
                    "Cet agent n'appartient pas à cette équipe."
            );
        }
    }

    /**
     * Change le rôle d'un agent dans une équipe.
     */
    public void changerRoleMembre(
            Long equipeId,
            Long agentId,
            MembreEquipe.RoleEquipe nouveauRole) {

        int lignes =
                membreRepository.changerRole(
                        equipeId,
                        agentId,
                        nouveauRole
                );

        if (lignes == 0) {
            throw new NoSuchElementException(
                    "Cet agent n'appartient pas à cette équipe."
            );
        }
    }

    /**
     * Liste les membres d'une équipe.
     */
    public List<MembreEquipe> listerMembres(
            Long equipeId) {

        obtenirEquipe(equipeId);

        return membreRepository.findByEquipeId(
                equipeId
        );
    }
}