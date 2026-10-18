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
}
