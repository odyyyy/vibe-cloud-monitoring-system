package com.monitoring.auth.organization.exception;

public class OrganizationNotFoundException extends RuntimeException {

    public OrganizationNotFoundException(Long id) {
        super("Organization " + id + " not found");
    }
}
