package com.RH.rh.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Exécuté une seule fois, juste après une authentification réussie
 * (soumission du formulaire /login validée).
 */
@Component
public class CookieAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(CookieAuthenticationSuccessHandler.class);

    public CookieAuthenticationSuccessHandler() {
        setDefaultTargetUrl("/"); // page vers laquelle rediriger après connexion
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {

        String username = authentication.getName();

        log.info("Connexion réussie pour l'utilisateur : {}", username);

        Cookie cookie = new Cookie("utilisateur", username);
        cookie.setPath("/");
        cookie.setHttpOnly(true);          // inaccessible en JS, protège contre le XSS
        cookie.setMaxAge(24 * 60 * 60);    // 1 jour, à ajuster
        // cookie.setSecure(true);         // à activer en production (HTTPS uniquement)

        response.addCookie(cookie);

        response.sendRedirect("/home");

        // Poursuit le comportement standard : redirection vers defaultTargetUrl
        super.onAuthenticationSuccess(request, response, authentication);
    }
}