package com.monitoring.storage.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyHasherTest {

    @Test
    void hash_isDeterministicLowercaseHexOf64Chars() {
        String hash = ApiKeyHasher.hash("app_1234");

        assertEquals(hash, ApiKeyHasher.hash("app_1234"));
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"), "expected lowercase hex SHA-256: " + hash);
    }

    @Test
    void hash_differsForDifferentKeys() {
        assertNotEquals(ApiKeyHasher.hash("app_a"), ApiKeyHasher.hash("app_b"));
    }

    @Test
    void hash_doesNotReturnThePlaintext() {
        assertNotEquals("app_secret", ApiKeyHasher.hash("app_secret"));
    }

    @Test
    void hash_rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> ApiKeyHasher.hash(null));
    }
}
