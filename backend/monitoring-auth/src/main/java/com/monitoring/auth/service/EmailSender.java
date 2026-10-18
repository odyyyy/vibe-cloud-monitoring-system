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

    /**
     * Sends the confirmation link for a pending email change to the <em>new</em> address.
     * Delivery to the new address is what proves the user controls it.
     */
    void sendEmailChangeConfirmation(String recipient, String username, String confirmationLink);

    /**
     * Notifies the <em>current</em> address that an email change was requested, so a change
     * initiated from a hijacked session does not go unnoticed. The raw link is never sent here;
     * only the requested new address is included.
     */
    void sendEmailChangeNotice(String recipient, String username, String newEmail);
}
