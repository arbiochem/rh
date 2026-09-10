package com.RH.rh.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.Statement;

@Component
public class AdminInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public AdminInitializer(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {

        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {

        String username = "admin";
        String password = "admin123";

        String hash = passwordEncoder.encode(password);

        try {
            /*
             * Vérifier si admin existe
             */
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM utilisateurs WHERE username = ?",
                    Integer.class,
                    username
            );

            if (count != null && count > 0) {

                /*
                 * Utilisateur existant :
                 * on utilise une Statement classique
                 * au lieu de PreparedStatement.executeUpdate()
                 */
                jdbcTemplate.execute((Connection connection) -> {

                    try (Statement statement = connection.createStatement()) {

                        String sql = """
                            UPDATE utilisateurs
                            SET password = '%s',
                                role = 'ADMIN',
                                actif = 1
                            WHERE username = '%s'
                            """.formatted(
                                escapeSql(hash),
                                escapeSql(username)
                        );

                        statement.execute(sql);
                    }

                    return null;
                });

                System.out.println();
                System.out.println("==============================================");
                System.out.println(" COMPTE ADMIN INITIALISE");
                System.out.println(" Username : admin");
                System.out.println(" Password : admin123");
                System.out.println("==============================================");
                System.out.println();

            } else {

                /*
                 * Création de l'utilisateur admin
                 */
                jdbcTemplate.execute((Connection connection) -> {

                    try (Statement statement = connection.createStatement()) {

                        String sql = """
                            INSERT INTO utilisateurs
                                (username, password, role, actif)
                            VALUES
                                ('%s', '%s', 'ADMIN', 1)
                            """.formatted(
                                escapeSql(username),
                                escapeSql(hash)
                        );

                        statement.execute(sql);
                    }

                    return null;
                });

                System.out.println();
                System.out.println("==============================================");
                System.out.println(" COMPTE ADMIN CREE");
                System.out.println(" Username : admin");
                System.out.println(" Password : admin123");
                System.out.println("==============================================");
                System.out.println();
            }

        } catch (Exception e) {

            System.err.println();
            System.err.println("==============================================");
            System.err.println(" ERREUR INITIALISATION ADMIN");
            System.err.println("==============================================");
            e.printStackTrace();
            System.err.println("==============================================");
            System.err.println();

            throw new RuntimeException(
                    "Impossible d'initialiser le compte administrateur",
                    e
            );
        }
    }

    /**
     * Protection minimale contre les apostrophes
     * lors de l'utilisation d'une Statement.
     */
    private String escapeSql(String value) {

        if (value == null) {
            return "";
        }

        return value.replace("'", "''");
    }
}
