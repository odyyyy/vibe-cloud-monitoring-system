package com.monitoring.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the SSO entry point exists and fails closed when SSO is not configured: with the
 * default configuration the endpoint reports {@code 404} instead of starting an OIDC flow.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sso-it;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class SsoEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ssoLoginIsUnavailableWhenDisabled() throws Exception {
        mockMvc.perform(get("/api/auth/sso/login"))
                .andExpect(status().isNotFound());
    }
}
