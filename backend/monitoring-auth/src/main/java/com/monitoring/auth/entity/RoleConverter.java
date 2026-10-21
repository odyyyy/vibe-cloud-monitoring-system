package com.monitoring.auth.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores the platform role as its name.
 *
 * <p>Unknown or legacy values (for example the {@code "ADMIN"} placeholder that older
 * installations may still contain) are mapped to {@link Role#USER}: an unrecognized role must
 * never grant platform privileges, and it must not break loading the account either.
 */
@Converter(autoApply = true)
public class RoleConverter implements AttributeConverter<Role, String> {

    @Override
    public String convertToDatabaseColumn(Role role) {
        return role == null ? null : role.name();
    }

    @Override
    public Role convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return Role.USER;
        }
        try {
            return Role.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            return Role.USER;
        }
    }
}
