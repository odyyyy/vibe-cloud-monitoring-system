package com.monitoring.auth.controller;

import com.monitoring.auth.config.SsoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class SsoControllerTest {

    @Test
    void loginReturnsNotFoundWhenSsoIsDisabled() {
        SsoProperties properties = new SsoProperties();
        properties.setEnabled(false);
        SsoController controller = new SsoController(properties);

        ResponseEntity<Void> response = controller.login();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void loginRedirectsToAuthorizationEndpointWhenEnabled() {
        SsoProperties properties = new SsoProperties();
        properties.setEnabled(true);
        properties.setRegistrationId("sso");
        SsoController controller = new SsoController(properties);

        ResponseEntity<Void> response = controller.login();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).hasToString("/oauth2/authorization/sso");
    }
}
