package com.monitoring.auth.service;

import com.monitoring.auth.dto.pat.PatSummary;
import com.monitoring.auth.entity.PersonalAccessToken;
import com.monitoring.auth.exception.InvalidPatScopeException;
import com.monitoring.auth.exception.PatNotFoundException;
import com.monitoring.auth.repository.PersonalAccessTokenRepository;
import com.monitoring.auth.security.PatScopes;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Creates, lists, revokes and verifies Personal Access Tokens.
 *
 * <p>Only the SHA-256 hash of a token is persisted; the raw value is returned once at
 * creation time and can never be recovered afterwards. Verification is a hash lookup plus an
 * expiry check, so a revoked (deleted) or expired token never authenticates.
 */
@Service
@RequiredArgsConstructor
public class PersonalAccessTokenService {

    static final String PAT_PREFIX = "pat_";
    private static final int PAT_TOKEN_BYTES = 32;
    private static final int HASH_HEX_LENGTH = 64;

    private final PersonalAccessTokenRepository tokenRepository;

    /** Cryptographically strong source for token bytes; reused because it is thread-safe. */
    private final SecureRandom secureRandom = new SecureRandom();

    /** The raw token and the persisted entity produced by {@link #generateToken}. */
    public record GeneratedToken(String rawToken, PersonalAccessToken token) {
    }

    @Transactional
    public GeneratedToken generateToken(Long userId,
                                        Long organizationId,
                                        String name,
                                        Set<String> scopes,
                                        int expiresDays) {
        validateScopes(scopes);

        String rawToken = generateRawToken();
        PersonalAccessToken token = PersonalAccessToken.builder()
                .userId(userId)
                .organizationId(organizationId)
                .name(name.trim())
                .tokenHash(hashToken(rawToken))
                .expiresAt(Instant.now().plus(expiresDays, ChronoUnit.DAYS))
                .scopes(Set.copyOf(scopes))
                .build();

        return new GeneratedToken(rawToken, tokenRepository.save(token));
    }

    @Transactional(readOnly = true)
    public List<PatSummary> listActiveTokens(Long userId, Long organizationId) {
        return tokenRepository
                .findByUserIdAndOrganizationIdAndExpiresAtAfterOrderByCreatedAtDesc(
                        userId, organizationId, Instant.now())
                .stream()
                .map(token -> new PatSummary(
                        token.getId(),
                        token.getName(),
                        token.getExpiresAt(),
                        token.getLastUsedAt(),
                        new ArrayList<>(token.getScopes())))
                .toList();
    }

    @Transactional
    public void revokeToken(Long tokenId, Long userId, Long organizationId) {
        PersonalAccessToken token = tokenRepository
                .findByIdAndUserIdAndOrganizationId(tokenId, userId, organizationId)
                .orElseThrow(PatNotFoundException::new);
        tokenRepository.delete(token);
    }

    /**
     * Best-effort, asynchronous update of {@code lastUsedAt}. Runs on the bounded
     * {@code patExecutor}; the caller only schedules it once per minute, so a burst of API
     * calls cannot turn into a burst of writes. A dropped task is harmless because the
     * timestamp is diagnostic only.
     */
    @Async("patExecutor")
    @Transactional
    public void markLastUsedAsync(Long tokenId) {
        tokenRepository.findById(tokenId).ifPresent(token -> {
            token.setLastUsedAt(Instant.now());
            tokenRepository.save(token);
        });
    }

    @Transactional(readOnly = true)
    public Optional<PatAuthResult> authenticate(String rawToken) {
        if (rawToken == null || !rawToken.startsWith(PAT_PREFIX)) {
            return Optional.empty();
        }

        return tokenRepository.findByTokenHash(hashToken(rawToken))
                .filter(token -> token.getExpiresAt().isAfter(Instant.now()))
                .map(token -> new PatAuthResult(
                        token.getId(),
                        token.getUserId(),
                        token.getOrganizationId(),
                        Set.copyOf(token.getScopes()),
                        token.getLastUsedAt()));
    }

    private void validateScopes(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            throw new InvalidPatScopeException("At least one scope is required");
        }
        for (String scope : scopes) {
            if (!PatScopes.isValid(scope)) {
                throw new InvalidPatScopeException("Unsupported scope: " + scope);
            }
        }
    }

    String generateRawToken() {
        byte[] bytes = new byte[PAT_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return PAT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hashed);
            if (hex.length() != HASH_HEX_LENGTH) {
                throw new IllegalStateException("Unexpected SHA-256 length");
            }
            return hex;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
