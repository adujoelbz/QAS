package com.example.qas.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
public class DebugController {

    @GetMapping("/api/debug/auth")
    public String authInfo(Authentication authentication) {
        if (authentication == null) {
            return "Not authenticated";
        }
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(", "));
        return "Principal: " + authentication.getName() + "\nAuthorities: " + authorities;
    }
}