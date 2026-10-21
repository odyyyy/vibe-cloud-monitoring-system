package com.monitoring.api;

import com.monitoring.auth.entity.Role;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the login brute-force guard against the real application context and
 * persistence. The threshold is lowered and the lockout made long so lockouts are observable
 * without waiting.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:login-bruteforce-it;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.auth.login-protection.max-attempts=3",
        "app.auth.login-protection.lockout-base=PT5M",
        "app.auth.login-protection.lockout-max=PT5M"
})
@AutoConfigureMockMvc
class LoginBruteForceIntegrationTest {

    private static final String USERNAME = "ada";
    private static final String PASSWORD = "supersecret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .username(USERNAME)
                .email("ada@example.com")
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.USER)
                .emailVerified(true)
                .build());
    }

    @Test
    void locksAfterRepeatedFailuresAndStaysLockedForCorrectPassword() throws Exception {
        String ip = "203.0.113.1";
        for (int attempt = 0; attempt < 3; attempt++) {
            login(USERNAME, "wrong", ip).andExpect(status().isUnauthorized());
        }

        login(USERNAME, "wrong", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value("Too many failed sign-in attempts. Please try again later."));

        login(USERNAME, PASSWORD, ip).andExpect(status().isTooManyRequests());
    }

    @Test
    void lockoutIsNotSharedAcrossClientIps() throws Exception {
        String attackerIp = "203.0.113.2";
        for (int attempt = 0; attempt < 3; attempt++) {
            login(USERNAME, "wrong", attackerIp).andExpect(status().isUnauthorized());
        }

        login(USERNAME, PASSWORD, "203.0.113.3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void lockoutIsNotSharedAcrossIdentifiers() throws Exception {
        String ip = "203.0.113.4";
        for (int attempt = 0; attempt < 3; attempt++) {
            login(USERNAME, "wrong", ip).andExpect(status().isUnauthorized());
        }

        login("grace", "wrong", ip).andExpect(status().isUnauthorized());
    }

    private ResultActions login(String identifier, String password, String forwardedFor) throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("username", identifier);
        body.put("password", password);

        MockHttpServletRequestBuilder request = post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return mockMvc.perform(request);
    }
}
