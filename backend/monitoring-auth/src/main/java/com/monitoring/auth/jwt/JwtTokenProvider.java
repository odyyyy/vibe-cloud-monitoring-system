package com.monitoring.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/**
 * Signs and verifies the HS256 JSON Web Tokens used for human sessions.
 *
 * <p>The signing secret is mandatory and must be at least 256 bits (32 bytes: the minimum key
 * size for HS256). There is deliberately no hard-coded fallback: a missing or weak secret fails
 * startup instead of silently signing tokens with a publicly known key.
 */
@Component
public class JwtTokenProvider {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration:86400000}") long expirationMillis) {
        byte[] keyBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret must be at least " + MIN_SECRET_BYTES
                            + " bytes (HS256); set the JWT_SECRET environment variable");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies the token signature and expiry in a single parse and returns the {@code uid}
     * claim. Returns {@link Optional#empty()} for a missing, malformed, expired or foreign token,
     * so callers treat every failure uniformly.
     */
    public Optional<Long> parseUserId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Object uid = claims.get("uid");
            return uid instanceof Number number ? Optional.of(number.longValue()) : Optional.empty();
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
