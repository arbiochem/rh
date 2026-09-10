package com.RH.rh.config;// adapte le package à ton projet

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Rend l'utilisateur connecté disponible dans TOUS les templates Thymeleaf,
 * en le lisant depuis le cookie "utilisateur" posé à la connexion
 * (voir CookieAuthenticationSuccessHandler).
 *
 * Le fragment de nav peut alors faire simplement :
 *   <strong th:text="${utilisateurConnecte}">Utilisateur</strong>
 */
@ControllerAdvice
public class GlobalModelAttributes {

    @ModelAttribute("utilisateurConnecte")
    public String utilisateurConnecte(HttpServletRequest request) {

        System.out.println(">>> GlobalModelAttributes appelé, cookies = "
                + java.util.Arrays.toString(request.getCookies()));

        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {
            if ("utilisateur".equals(cookie.getName())) {
                System.out.println(">>> cookie utilisateur trouvé : " + cookie.getValue());
                return cookie.getValue();
            }
        }

        return null;
    }
}