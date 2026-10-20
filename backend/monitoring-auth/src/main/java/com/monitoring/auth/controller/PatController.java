package com.monitoring.auth.controller;

import com.monitoring.auth.dto.pat.PatCreateRequest;
import com.monitoring.auth.dto.pat.PatCreateResponse;
import com.monitoring.auth.dto.pat.PatListResponse;
import com.monitoring.auth.dto.pat.PatRevokeResponse;
import com.monitoring.auth.dto.pat.PatSummary;
import com.monitoring.auth.entity.PersonalAccessToken;
import com.monitoring.auth.service.PersonalAccessTokenService;
import com.monitoring.organization.tenant.CurrentTenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Management endpoints for Personal Access Tokens. They are reachable with a human JWT only:
 * {@link com.monitoring.auth.jwt.PatAuthFilter} never authenticates a token on these paths, so
 * a token cannot create, list or revoke other tokens.
 *
 * <p>The owner and tenant are resolved from the authenticated identity and the active
 * organization (validated membership), never from the request body.
 */
@RestController
@RequestMapping("/api/v1/settings/pats")
@RequiredArgsConstructor
public class PatController {

    private final PersonalAccessTokenService patService;
    private final CurrentTenantService currentTenantService;

    @PostMapping
    public ResponseEntity<PatCreateResponse> createPat(@Valid @RequestBody PatCreateRequest request) {
        Long userId = currentTenantService.requireUserId();
        Long organizationId = currentTenantService.requireOrganizationId();

        PersonalAccessTokenService.GeneratedToken generated = patService.generateToken(
                userId,
                organizationId,
                request.name(),
                Set.copyOf(request.scopes()),
                request.expiresDays());

        PersonalAccessToken token = generated.token();
        PatCreateResponse response = new PatCreateResponse(
                generated.rawToken(),
                token.getId(),
                token.getName(),
                token.getExpiresAt(),
                Set.copyOf(token.getScopes()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PatListResponse> listPats() {
        Long userId = currentTenantService.requireUserId();
        Long organizationId = currentTenantService.requireOrganizationId();
        List<PatSummary> summaries = patService.listActiveTokens(userId, organizationId);
        return ResponseEntity.ok(new PatListResponse(summaries));
    }

    @DeleteMapping("/{tokenId}")
    public ResponseEntity<PatRevokeResponse> revokePat(@PathVariable Long tokenId) {
        Long userId = currentTenantService.requireUserId();
        Long organizationId = currentTenantService.requireOrganizationId();
        patService.revokeToken(tokenId, userId, organizationId);
        return ResponseEntity.ok(new PatRevokeResponse(tokenId, "Token revoked successfully"));
    }
}
