package com.monitoring.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration for the email-confirmation and password-reset flows.
 *
 * <p>Email delivery is intentionally pluggable: until an SMTP integration is added, links are
 * written to the application log ({@code app.auth.log-emails}) so local development works
 * without an external provider or committed secrets.
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

    /** Frontend base URL used to build the confirmation link. */
    private String verificationUrl = "http://localhost:3000/verify-email";

    /** Frontend base URL used to build the password-reset link. */
    private String resetUrl = "http://localhost:3000/reset-password";

    /** When true, outbound emails (with their one-time links) are written to the log. */
    private boolean logEmails = true;
}
