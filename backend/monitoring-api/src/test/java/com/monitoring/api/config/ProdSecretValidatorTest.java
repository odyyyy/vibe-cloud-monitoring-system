package com.monitoring.api.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdSecretValidatorTest {

    @Test
    void rejectsMissingSecret() {
        ProdSecretValidator validator = new ProdSecretValidator("");

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET is missing");
    }

    @Test
    void rejectsShortSecret() {
        ProdSecretValidator validator = new ProdSecretValidator("too-short");

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void rejectsExampleSecret() {
        ProdSecretValidator validator = new ProdSecretValidator(
                "replace-with-a-random-secret-at-least-32-bytes");

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insecure default");
    }

    @Test
    void acceptsStrongSecret() {
        ProdSecretValidator validator = new ProdSecretValidator(
                "c1f2e3d4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4");

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }
}
