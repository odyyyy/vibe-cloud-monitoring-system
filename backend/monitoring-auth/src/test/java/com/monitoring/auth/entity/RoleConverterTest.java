package com.monitoring.auth.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleConverterTest {

    private final RoleConverter converter = new RoleConverter();

    @Test
    void knownRoleNamesAreMapped() {
        assertEquals(Role.USER, converter.convertToEntityAttribute("USER"));
        assertEquals(Role.PLATFORM_ADMIN, converter.convertToEntityAttribute("PLATFORM_ADMIN"));
    }

    @Test
    void unknownAndLegacyValuesFallBackToUser() {
        assertEquals(Role.USER, converter.convertToEntityAttribute("ADMIN"));
        assertEquals(Role.USER, converter.convertToEntityAttribute("root"));
        assertEquals(Role.USER, converter.convertToEntityAttribute(null));
        assertEquals(Role.USER, converter.convertToEntityAttribute("  "));
    }

    @Test
    void roleIsStoredByName() {
        assertEquals("PLATFORM_ADMIN", converter.convertToDatabaseColumn(Role.PLATFORM_ADMIN));
        assertEquals("USER", converter.convertToDatabaseColumn(Role.USER));
        assertEquals(null, converter.convertToDatabaseColumn(null));
    }
}
