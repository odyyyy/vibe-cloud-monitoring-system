package com.monitoring.auth.controller;

import com.monitoring.auth.config.SsoProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Entry point that starts the OIDC login flow.
 *
 * <p>The endpoint always exists so clients get a predictable answer: when SSO is disabled it
 * responds {@code 404}, and when it is enabled it redirects to Spring Security's authorization
 * endpoint, which then redirects to the configured provider.
 */
@RestController
@RequestMapping("/api/auth/sso")
@RequiredArgsConstructor
public class SsoController {

    private final SsoProperties ssoProperties;

    @GetMapping("/login")
    public ResponseEntity<Void> login() {
        if (!ssoProperties.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        URI authorizationEndpoint = URI.create("/oauth2/authorization/" + ssoProperties.getRegistrationId());
        return ResponseEntity.status(HttpStatus.FOUND).location(authorizationEndpoint).build();
    }
}
