package com.RH.rh.repository;

import com.RH.rh.model.Affectation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AffectationRepository extends JpaRepository<Affectation, Long> {

    List<Affectation> findByDateAffectationBetweenOrderByDateAffectationAscIdAsc(LocalDate debut, LocalDate fin);

    List<Affectation> findByDateAffectation(LocalDate date);

    List<Affectation> findByAgentIdAndDateAffectationBetween(Long agentId, LocalDate debut, LocalDate fin);
}
