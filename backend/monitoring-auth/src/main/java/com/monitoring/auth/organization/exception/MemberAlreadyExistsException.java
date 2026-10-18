package com.monitoring.auth.organization.exception;

public class MemberAlreadyExistsException extends RuntimeException {

    public MemberAlreadyExistsException() {
        super("This user is already a member of the organization");
    }
}
