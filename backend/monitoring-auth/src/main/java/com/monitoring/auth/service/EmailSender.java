package com.monitoring.auth.service;

/**
 * Delivers transactional authentication emails.
 *
 * <p>Implemented today by {@link LoggingEmailSender} (log-only, for local development); an
 * SMTP/production implementation can be added without changing {@link AuthService}.
 */
public interface EmailSender {

    void sendEmailVerification(String recipient, String username, String verificationLink);

    void sendPasswordReset(String recipient, String username, String resetLink);
}
