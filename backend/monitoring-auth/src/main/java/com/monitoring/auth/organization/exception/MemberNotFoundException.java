package com.monitoring.auth.organization.exception;

public class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException() {
        super("Member not found in this organization");
    }
}
