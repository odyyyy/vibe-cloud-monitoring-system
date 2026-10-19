package com.monitoring.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration for the email-confirmation and password-reset flows.
 *
 * <p>Email delivery is intentionally pluggable: until an SMTP integration is added, links can be
 * written to the application log ({@code app.auth.log-emails}) so local development works
 * without an external provider or committed secrets. Logging is disabled by default because
 * the emitted links are one-time credentials; enable it only in local profiles.
 */
@Component
@ConfigurationProperties(prefix = "app.auth")
@Getter
@Setter
public class AuthProperties {

    /** How long an email-confirmation link stays valid. */
    private Duration verificationTtl = Duration.ofHours(24);

    /** How long a password-reset link stays valid. */
    private Duration resetTtl = Duration.ofHours(1);

    /** How long an email-change confirmation link stays valid. */
    private Duration emailChangeTtl = Duration.ofHours(1);

    /** Frontend base URL used to build the confirmation link. */
    private String verificationUrl = "http://localhost:3000/verify-email";

    /** Frontend base URL used to build the password-reset link. */
    private String resetUrl = "http://localhost:3000/reset-password";

    /** Frontend base URL used to build the email-change confirmation link. */
    private String emailChangeUrl = "http://localhost:3000/confirm-email-change";

    /**
     * When true, outbound emails (with their one-time links) are written to the log.
     * Disabled by default: verification and reset links are credentials and must not
     * end up in production logs. The {@code dev} profile enables it explicitly.
     */
    private boolean logEmails = false;

    /** Brute-force protection for password sign-in. */
    private LoginProtection loginProtection = new LoginProtection();

    /**
     * Configuration for the in-memory login brute-force guard.
     *
     * <p>Failed attempts are counted per identifier and client IP inside {@code failureWindow}.
     * Once {@code maxAttempts} is reached the pair is locked for {@code lockoutBase}, doubling
     * on each consecutive lockout up to {@code lockoutMax}. Set {@code enabled=false} to turn
     * throttling off (for example in tests or local development).
     */
    @Getter
    @Setter
    public static class LoginProtection {

        /** When false, failed attempts are never counted or blocked. */
        private boolean enabled = true;

        /** Failed attempts allowed inside the window before the key is locked. */
        private int maxAttempts = 5;

        /** How long failures are counted before the counter (and escalation) resets. */
        private Duration failureWindow = Duration.ofMinutes(15);

        /** First lockout duration; doubles with each consecutive lockout. */
        private Duration lockoutBase = Duration.ofMinutes(1);

        /** Upper bound on a single lockout duration. */
        private Duration lockoutMax = Duration.ofMinutes(15);

        /** Hard cap on tracked identifier/IP keys to bound memory use. */
        private int maxTrackedKeys = 100_000;
    }
}
