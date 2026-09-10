package com.RH.rh.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class BootstrapDataInitializer {

    @Bean
    public CommandLineRunner creerAdminParDefaut(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {

        return args -> {

            String username = "admin";
            String password = "admin123";

            /*
             * Vérifier si le compte admin existe
             */
            Integer count = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM utilisateurs
                    WHERE username = ?
                    """,
                    Integer.class,
                    username
            );

            if (count == null || count == 0) {

                String hash = passwordEncoder.encode(password);

                /*
                 * Création du compte admin
                 */
                jdbcTemplate.update(
                        """
                        INSERT INTO utilisateurs
                            (username, password, role, actif)
                        VALUES
                            (?, ?, ?, ?)
                        """,
                        username,
                        hash,
                        "ADMIN",
                        1
                );

                System.out.println();
                System.out.println("==============================================");
                System.out.println(" COMPTE ADMIN CREE");
                System.out.println(" Username : admin");
                System.out.println(" Password : admin123");
                System.out.println("==============================================");
                System.out.println();

            } else {

                System.out.println();
                System.out.println("==============================================");
                System.out.println(" COMPTE ADMIN DEJA PRESENT");
                System.out.println("==============================================");
                System.out.println();
            }
        };
    }
}
