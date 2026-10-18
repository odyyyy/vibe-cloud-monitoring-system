package com.monitoring.auth.organization.exception;

/**
 * Invitation target is not a registered user. The MVP has no pending-invite persistence, so
 * the email must belong to an existing account (the mock email flow reports this).
 */
public class InviteTargetNotFoundException extends RuntimeException {

    public InviteTargetNotFoundException() {
        super("No registered user with that email address");
    }
}
