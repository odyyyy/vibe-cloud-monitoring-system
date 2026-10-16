package com.monitoring.auth.service;

import com.monitoring.auth.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Development {@link EmailSender} that writes the outgoing email to the application log
 * instead of contacting an SMTP server.
 *
 * <p>This is the only implementation until a real mail provider is integrated. That means
 * the one-time link (and therefore the token) is written to the log, which is acceptable only
 * for local development. Set {@code app.auth.log-emails=false} to suppress it once a real
 * sender is wired in.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingEmailSender implements EmailSender {

    private final AuthProperties authProperties;

    @Override
    public void sendEmailVerification(String recipient, String username, String verificationLink) {
        if (!authProperties.isLogEmails()) {
            return;
        }
        log.info("[email] Confirm your email for {} ({}) -> {}", username, recipient, verificationLink);
    }

    @Override
    public void sendPasswordReset(String recipient, String username, String resetLink) {
        if (!authProperties.isLogEmails()) {
            return;
        }
        log.info("[email] Reset your password for {} ({}) -> {}", username, recipient, resetLink);
    }
}
