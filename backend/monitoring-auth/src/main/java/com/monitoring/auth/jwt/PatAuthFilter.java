package com.monitoring.auth.jwt;

import com.monitoring.auth.security.PatScopePolicy;
import com.monitoring.auth.service.PatAuthResult;
import com.monitoring.auth.service.PersonalAccessTokenService;
import com.monitoring.organization.tenant.CurrentTenantService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authenticates {@code Authorization: Bearer pat_...} requests as the user that owns the
 * Personal Access Token.
 *
 * <p>On top of authentication the filter enforces two invariants:
 * <ul>
 *   <li>the token is bound to one organization: a different {@code X-Organization-Id} is
 *   rejected instead of letting the token act in another tenant;</li>
 *   <li>the route must be covered by one of the token's scopes, otherwise the request is
 *   rejected with {@code 403}. Routes without a rule are denied by default.</li>
 * </ul>
 *
 * <p>Token management endpoints ({@code /api/v1/settings/pats}) are deliberately left to the
 * JWT filter, so a token can never create, list or revoke tokens.
 */
@Component
@RequiredArgsConstructor
public class PatAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PAT_PREFIX = "pat_";
    private static final String SETTINGS_PAT_PATH = "/api/v1/settings/pats";
    private static final String SCOPE_AUTHORITY_PREFIX = "SCOPE_";

    /** Tokens are touched at most once per this interval to avoid a write per API call. */
    private static final Duration LAST_USED_THROTTLE = Duration.ofMinutes(1);

    private final PersonalAccessTokenService patService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = bearerToken(request);
        if (token == null
                || !token.startsWith(PAT_PREFIX)
                || isSettingsPatPath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        PatAuthResult authResult = patService.authenticate(token).orElse(null);
        if (authResult == null) {
            // Unknown, expired or revoked token: leave the context anonymous so the security
            // entry point answers 401 for protected routes.
            filterChain.doFilter(request, response);
            return;
        }

        if (!organizationMatches(request, authResult.organizationId())) {
            reject(response, HttpServletResponse.SC_FORBIDDEN,
                    "This token is not valid for the requested organization");
            return;
        }

        if (!scopeAllows(request, authResult.scopes())) {
            reject(response, HttpServletResponse.SC_FORBIDDEN,
                    "This token does not grant access to this resource");
            return;
        }

        setAuthentication(authResult);
        touchIfStale(authResult);

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(PatAuthResult authResult) {
        Set<SimpleGrantedAuthority> authorities = authResult.scopes().stream()
                .map(scope -> new SimpleGrantedAuthority(SCOPE_AUTHORITY_PREFIX + scope))
                .collect(Collectors.toSet());

        var authentication = new UsernamePasswordAuthenticationToken(
                authResult.userId().toString(), null, authorities);
        authentication.setDetails(new PatAuthenticationDetails(
                authResult.tokenId(),
                authResult.organizationId(),
                authResult.scopes(),
                authResult.lastUsedAt()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void touchIfStale(PatAuthResult authResult) {
        Instant lastUsedAt = authResult.lastUsedAt();
        if (lastUsedAt == null || lastUsedAt.isBefore(Instant.now().minus(LAST_USED_THROTTLE))) {
            patService.markLastUsedAsync(authResult.tokenId());
        }
    }

    private boolean scopeAllows(HttpServletRequest request, Set<String> scopes) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        if (PatScopePolicy.isPublic(uri)) {
            return true;
        }
        return PatScopePolicy.requiredScope(request.getMethod(), uri)
                .map(scopes::contains)
                .orElse(false);
    }

    private boolean organizationMatches(HttpServletRequest request, Long organizationId) {
        String header = request.getHeader(CurrentTenantService.ORGANIZATION_HEADER);
        if (header == null || header.isBlank()) {
            return true;
        }
        try {
            return Long.valueOf(header.trim()).equals(organizationId);
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }

    private boolean isSettingsPatPath(String uri) {
        return uri.equals(SETTINGS_PAT_PATH) || uri.startsWith(SETTINGS_PAT_PATH + "/");
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
