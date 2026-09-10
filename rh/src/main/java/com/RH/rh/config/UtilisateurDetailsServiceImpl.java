package com.RH.rh.config;

import com.RH.rh.repository.UtilisateurRepository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class UtilisateurDetailsServiceImpl implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;

    public UtilisateurDetailsServiceImpl(UtilisateurRepository utilisateurRepository) {
        this.utilisateurRepository = utilisateurRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        Map<String, Object> utilisateur;

        try {
            utilisateur = utilisateurRepository.findByUsername(username);
        } catch (EmptyResultDataAccessException e) {
            throw new UsernameNotFoundException("Utilisateur introuvable : " + username);
        }

        String password = (String) utilisateur.get("password");
        String role = (String) utilisateur.get("role");
        Object actifObj = utilisateur.get("actif");
        boolean actif = actifObj != null && (
                actifObj.equals(1L) || actifObj.equals(1) || Boolean.TRUE.equals(actifObj)
        );

        return User.builder()
                .username((String) utilisateur.get("username"))
                .password(password)
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .disabled(!actif)
                .build();
    }
}