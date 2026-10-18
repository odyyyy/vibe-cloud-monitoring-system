package com.monitoring.auth.service;

import com.monitoring.auth.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Development {@link EmailSender} that writes the outgoing email to the application log
 * instead of contacting an SMTP server.
 *
 * <p>This is the only implementation until a real mail provider is integrated. It is disabled
 * by default ({@code app.auth.log-emails=false}) because the one-time link contains the token:
 * the {@code dev} profile enables it for local development, and any other environment should
 * keep it off or replace this sender with a real implementation.
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

    @Override
    public void sendEmailChangeConfirmation(String recipient, String username, String confirmationLink) {
        if (!authProperties.isLogEmails()) {
            return;
        }
        log.info("[email] Confirm your new email for {} ({}) -> {}", username, recipient, confirmationLink);
    }

    @Override
    public void sendEmailChangeNotice(String recipient, String username, String newEmail) {
        if (!authProperties.isLogEmails()) {
            return;
        }
        log.info("[email] Email change requested for {} ({}); new address: {}", username, recipient, newEmail);
    }
}
