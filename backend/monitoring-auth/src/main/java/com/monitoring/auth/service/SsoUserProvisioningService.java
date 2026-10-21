package com.monitoring.auth.service;

import com.monitoring.auth.config.SsoProperties;
import com.monitoring.auth.entity.Role;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.exception.SsoAuthenticationException;
import com.monitoring.auth.repository.UserRepository;
import com.monitoring.organization.entity.MembershipRole;
import com.monitoring.organization.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;

/**
 * Just-in-time provisioning of local accounts from an OIDC identity.
 *
 * <p>On every SSO login the identity is reconciled with a local {@link User}: an account is
 * created on first sight (matched by email), the mapped role is applied to the user's personal
 * organization, and the email is treated as verified because the identity provider asserted it.
 * Accounts created this way get a random password they can never know, so local password login is
 * effectively disabled for them while remaining available for everyone else.
 */
@Service
@RequiredArgsConstructor
public class SsoUserProvisioningService {

    private static final int USERNAME_MIN_LENGTH = 3;
    private static final int USERNAME_MAX_LENGTH = 50;
    private static final int RANDOM_PASSWORD_BYTES = 32;
    private static final int MAX_USERNAME_ATTEMPTS = 1000;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationService organizationService;
    private final SsoProperties ssoProperties;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Finds or creates the local user for the given OIDC identity and applies the mapped role.
     *
     * @return the persisted local user
     * @throws SsoAuthenticationException if the provider did not return an email address
     */
    @Transactional
    public User provision(String email, String name, Collection<String> groups) {
        String normalizedEmail = normalizeEmail(email);
        MembershipRole role = resolveRole(groups);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> userRepository.save(newSsoUser(normalizedEmail, name)));

        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(Instant.now());
            user = userRepository.save(user);
        }

        organizationService.ensurePersonalMembership(user.getId(), user.getUsername(), role);
        return user;
    }

    /**
     * Resolves the platform role from the claimed groups. Mapping is strict: only an exact claim
     * value that appears in {@code app.sso.role-mapping} is honoured, and the most privileged
     * match wins. Without a match the configured default (VIEWER) is used.
     */
    MembershipRole resolveRole(Collection<String> groups) {
        MembershipRole defaultRole = ssoProperties.getDefaultRole() == null
                ? MembershipRole.VIEWER
                : ssoProperties.getDefaultRole();
        MembershipRole resolved = defaultRole;
        if (groups == null) {
            return resolved;
        }
        // Lower ordinal == more privileged (OWNER, ADMIN, VIEWER).
        for (String group : groups) {
            MembershipRole mapped = ssoProperties.getRoleMapping().get(group);
            if (mapped != null && mapped.ordinal() < resolved.ordinal()) {
                resolved = mapped;
            }
        }
        return resolved;
    }

    private User newSsoUser(String email, String name) {
        return User.builder()
                .username(uniqueUsername(email, name))
                .email(email)
                .password(passwordEncoder.encode(randomPassword()))
                .role(Role.USER)
                .emailVerified(true)
                .emailVerifiedAt(Instant.now())
                .build();
    }

    private String normalizeEmail(String email) {
        String value = email == null ? "" : email.trim();
        if (value.isEmpty()) {
            throw new SsoAuthenticationException(
                    "The identity provider did not return an email address");
        }
        return value;
    }

    private String uniqueUsername(String email, String name) {
        String base = usernameBase(email, name);
        if (base.length() < USERNAME_MIN_LENGTH) {
            base = base + "user";
        }
        if (base.length() > USERNAME_MAX_LENGTH) {
            base = base.substring(0, USERNAME_MAX_LENGTH);
        }
        if (!userRepository.existsByUsername(base)) {
            return base;
        }
        for (int i = 1; i < MAX_USERNAME_ATTEMPTS; i++) {
            String suffix = String.valueOf(i);
            String trimmed = base.length() + suffix.length() > USERNAME_MAX_LENGTH
                    ? base.substring(0, USERNAME_MAX_LENGTH - suffix.length())
                    : base;
            String candidate = trimmed + suffix;
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw new SsoAuthenticationException("Unable to allocate a unique username");
    }

    private String usernameBase(String email, String name) {
        if (name != null && !name.isBlank()) {
            String candidate = sanitize(name);
            if (candidate.length() >= USERNAME_MIN_LENGTH) {
                return candidate;
            }
        }
        int at = email.indexOf('@');
        return sanitize(at > 0 ? email.substring(0, at) : email);
    }

    private String sanitize(String value) {
        String cleaned = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9._-]", "");
        return cleaned.length() > USERNAME_MAX_LENGTH ? cleaned.substring(0, USERNAME_MAX_LENGTH) : cleaned;
    }

    private String randomPassword() {
        byte[] bytes = new byte[RANDOM_PASSWORD_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
