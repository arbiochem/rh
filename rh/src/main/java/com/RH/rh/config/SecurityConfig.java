package com.RH.rh.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// Dans ta config Spring Security existante :

@Configuration
public class SecurityConfig {

    private final CookieAuthenticationSuccessHandler cookieAuthenticationSuccessHandler;

    public SecurityConfig(CookieAuthenticationSuccessHandler cookieAuthenticationSuccessHandler) {
        this.cookieAuthenticationSuccessHandler = cookieAuthenticationSuccessHandler;
    }

    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/images/**", "/js/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(cookieAuthenticationSuccessHandler) // <-- remplace defaultSuccessUrl(...)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("utilisateur") // supprime le cookie à la déconnexion
                .permitAll()
            );

        return http.build();
    }
}
