package com.monitoring.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.auth.entity.AuthTokenType;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.repository.AuthTokenRepository;
import com.monitoring.auth.repository.UserRepository;
import com.monitoring.auth.service.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the email-confirmation and password-reset flows against the real
 * persistence context, so the V8 Flyway migration and Hibernate mapping are validated too.
 * Email delivery is replaced by a mock that captures the one-time links.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-it;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class AuthIntegrationTest {

    private static final String USERNAME = "ada";
    private static final String EMAIL = "ada@example.com";
    private static final String PASSWORD = "supersecret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @MockBean
    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        authTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registerVerifyLoginFlow() throws Exception {
        register();

        User created = userRepository.findByUsername(USERNAME).orElseThrow();
        assertThat(created.isEmailVerified()).isFalse();

        String token = captureToken(AuthTokenType.EMAIL_VERIFICATION);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(loginBody(PASSWORD))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(userRepository.findByUsername(USERNAME).orElseThrow().isEmailVerified()).isTrue();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(loginBody(PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPasswordFlow() throws Exception {
        register();
        String verificationToken = captureToken(AuthTokenType.EMAIL_VERIFICATION);
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", verificationToken))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", EMAIL))))
                .andExpect(status().isOk());

        String resetToken = captureToken(AuthTokenType.PASSWORD_RESET);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", resetToken, "newPassword", "brandnewpass"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(loginBody("brandnewpass"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", resetToken, "newPassword", "anotherpass"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        register();

        Map<String, String> duplicate = new LinkedHashMap<>();
        duplicate.put("username", "grace");
        duplicate.put("email", EMAIL);
        duplicate.put("password", PASSWORD);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(duplicate)))
                .andExpect(status().isConflict());
    }

    @Test
    void forgotPasswordDoesNotRevealUnknownAccounts() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nobody@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private void register() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(registerBody())))
                .andExpect(status().isOk());
    }

    private Map<String, String> registerBody() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("username", USERNAME);
        body.put("email", EMAIL);
        body.put("password", PASSWORD);
        return body;
    }

    private Map<String, String> loginBody(String password) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("username", USERNAME);
        body.put("password", password);
        return body;
    }

    private String json(Map<String, String> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String captureToken(AuthTokenType type) {
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        if (type == AuthTokenType.EMAIL_VERIFICATION) {
            verify(emailSender).sendEmailVerification(eq(EMAIL), eq(USERNAME), linkCaptor.capture());
        } else {
            verify(emailSender).sendPasswordReset(eq(EMAIL), eq(USERNAME), linkCaptor.capture());
        }
        String token = UriComponentsBuilder.fromUriString(linkCaptor.getValue())
                .build()
                .getQueryParams()
                .getFirst("token");
        assertThat(token).isNotBlank();
        return token;
    }
}
