package com.eecs3311.backend.config;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {
    @GetMapping("/api/csrf")
    public ResponseEntity<Void> token(CsrfToken token) {
        return ResponseEntity.noContent().header("X-CSRF-TOKEN", token.getToken())
                .header("Cache-Control", "no-store").build();
    }
}
