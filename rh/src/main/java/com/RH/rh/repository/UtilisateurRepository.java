package com.RH.rh.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class UtilisateurRepository {

    private final JdbcTemplate jdbcTemplate;

    public UtilisateurRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> findByUsername(String username) {

        return jdbcTemplate.queryForMap("""
            SELECT
                id,
                username,
                password,
                role,
                actif,
                created_at
            FROM utilisateurs
            WHERE username = ?
        """, username);
    }
}