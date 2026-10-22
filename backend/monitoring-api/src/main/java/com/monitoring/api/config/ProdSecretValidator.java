package com.monitoring.api.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Fails startup when the {@code prod} profile runs with a missing, weak or example JWT secret.
 *
 * <p>{@link com.monitoring.auth.jwt.JwtTokenProvider} already rejects secrets shorter than 32
 * bytes. This guard additionally rejects values that are long enough but are obvious
 * placeholders committed to the repository, so copying {@code .env.example} to production
 * cannot silently sign tokens with a publicly known key.
 */
@Component
@Profile("prod")
public class ProdSecretValidator implements InitializingBean {

    private static final int MIN_SECRET_BYTES = 32;

    private static final Set<String> INSECURE_SECRETS = Set.of(
            "change-me",
            "changeme",
            "change-me-please",
            "replace-with-a-random-secret-at-least-32-bytes",
            "replace-me-with-a-random-secret-at-least-32-bytes",
            "dev-secret",
            "dev-secret-change-me",
            "secret",
            "your-secret",
            "your-secret-key",
            "insecure-default-secret-change-me"
    );

    private final String jwtSecret;

    public ProdSecretValidator(@Value("${jwt.secret:}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @Override
    public void afterPropertiesSet() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "Refusing to start with the 'prod' profile: JWT_SECRET is missing. "
                            + "Set a random secret of at least 32 bytes in the environment.");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "Refusing to start with the 'prod' profile: JWT_SECRET must be at least "
                            + MIN_SECRET_BYTES + " bytes (HS256).");
        }
        String normalized = jwtSecret.trim().toLowerCase(Locale.ROOT);
        if (INSECURE_SECRETS.contains(normalized)
                || normalized.contains("change-me")
                || normalized.contains("replace-with")
                || normalized.contains("your-secret")) {
            throw new IllegalStateException(
                    "Refusing to start with the 'prod' profile: JWT_SECRET uses an example or "
                            + "insecure default value. Generate a unique random secret.");
        }
    }
}
