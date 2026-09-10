package com.RH.rh.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {


@Override
public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication)
        throws IOException, ServletException {

    System.out.println();
    System.out.println("==================================================");
    System.out.println("              CONNEXION REUSSIE");
    System.out.println("==================================================");
    System.out.println("Utilisateur : " + authentication.getName());
    System.out.println("Authorities : " + authentication.getAuthorities());
    System.out.println("==================================================");
    System.out.println();

    response.sendRedirect("/");
}

}
