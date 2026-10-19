package com.monitoring.auth.config;

import com.monitoring.organization.entity.MembershipRole;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuration for the optional OpenID Connect (SSO) login.
 *
 * <p>SSO is disabled by default ({@code app.sso.enabled=false}), so a plain deployment has no
 * dependency on an external identity provider and local password login keeps working. When it is
 * enabled the issuer, client id and secret are mandatory and the issuer is validated at startup:
 * a non-HTTPS issuer is rejected unless it points at localhost (a local mock provider).
 */
@Component
@ConfigurationProperties(prefix = "app.sso")
@Getter
@Setter
public class SsoProperties {

    /** Hosts for which a plain HTTP issuer is tolerated (local development/mock providers). */
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    /** Whether OIDC login is enabled. Disabled by default. */
    private boolean enabled = false;

    /** Spring Security client registration id used in the authorization endpoint path. */
    private String registrationId = "sso";

    /** OIDC issuer URL used for discovery ({@code .well-known/openid-configuration}). */
    private String issuerUri;

    private String clientId;

    private String clientSecret;

    private List<String> scopes = new ArrayList<>(List.of("openid", "email", "profile"));

    /** Name of the claim that carries the caller's groups/roles. */
    private String roleClaim = "groups";

    /**
     * Strict mapping from a claim value (e.g. an IDP group name) to a platform role. Values are
     * compared exactly; a claim value with no entry falls back to {@link #defaultRole}.
     */
    private Map<String, MembershipRole> roleMapping = new HashMap<>();

    /** Role granted when no claim value matches {@link #roleMapping}. */
    private MembershipRole defaultRole = MembershipRole.VIEWER;

    /** Frontend URL the JWT is handed back to after a successful login (token in the fragment). */
    private String successRedirectUrl = "http://localhost:3000/sso/callback";

    /** Frontend URL used when the OIDC exchange fails. */
    private String failureRedirectUrl = "http://localhost:3000/login";

    @PostConstruct
    void validate() {
        if (!enabled) {
            return;
        }
        require(issuerUri != null && !issuerUri.isBlank(),
                "app.sso.issuer-uri is required when SSO is enabled");
        require(clientId != null && !clientId.isBlank(),
                "app.sso.client-id is required when SSO is enabled");
        require(clientSecret != null && !clientSecret.isBlank(),
                "app.sso.client-secret is required when SSO is enabled");

        URI issuer;
        try {
            issuer = URI.create(issuerUri.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("app.sso.issuer-uri is not a valid URI", ex);
        }
        boolean https = "https".equalsIgnoreCase(issuer.getScheme());
        boolean local = issuer.getHost() != null && LOCAL_HOSTS.contains(issuer.getHost().toLowerCase());
        require(https || local,
                "app.sso.issuer-uri must use https (http is only allowed for a localhost issuer)");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
