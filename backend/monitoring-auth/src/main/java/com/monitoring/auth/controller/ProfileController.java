package com.monitoring.auth.controller;

import com.monitoring.auth.dto.response.MessageResponse;
import com.monitoring.auth.dto.request.UpdateEmailRequest;
import com.monitoring.auth.dto.request.UpdateUsernameRequest;
import com.monitoring.auth.dto.response.UserProfileResponse;
import com.monitoring.auth.dto.response.UsernameAvailabilityResponse;
import com.monitoring.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated self-service profile changes (username and email address).
 *
 * <p>These endpoints are deliberately <em>not</em> under {@code /api/auth/**}, which is public:
 * {@code /api/profile/**} is protected by the security chain, and both mutating calls re-check
 * the current password so a hijacked session cannot silently take over the account identity.
 * The identity of the caller is taken from the JWT principal, never from the request body.
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final AuthService authService;

    @PutMapping("/username")
    public ResponseEntity<UserProfileResponse> updateUsername(
            Authentication authentication, @Valid @RequestBody UpdateUsernameRequest request) {
        Long userId = currentUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(
                authService.updateUsername(userId, request.getCurrentPassword(), request.getUsername()));
    }

    @GetMapping("/username-available")
    public ResponseEntity<UsernameAvailabilityResponse> usernameAvailable(
            Authentication authentication, @RequestParam("username") String username) {
        Long userId = currentUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new UsernameAvailabilityResponse(authService.isUsernameAvailable(username)));
    }

    @PutMapping("/email")
    public ResponseEntity<MessageResponse> requestEmailChange(
            Authentication authentication, @Valid @RequestBody UpdateEmailRequest request) {
        Long userId = currentUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(
                authService.requestEmailChange(userId, request.getCurrentPassword(), request.getNewEmail()));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
