package com.monitoring.auth.organization.exception;

/**
 * The operation would leave the organization without any OWNER, or an OWNER is trying to
 * demote/remove themselves while being the last owner.
 */
public class LastOwnerException extends RuntimeException {

    public LastOwnerException() {
        super("The organization must keep at least one owner");
    }
}
