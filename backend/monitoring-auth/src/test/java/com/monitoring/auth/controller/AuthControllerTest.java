package com.monitoring.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.auth.dto.response.AuthResponse;
import com.monitoring.auth.dto.request.ForgotPasswordRequest;
import com.monitoring.auth.dto.request.LoginRequest;
import com.monitoring.auth.dto.response.MessageResponse;
import com.monitoring.auth.dto.request.RegisterRequest;
import com.monitoring.auth.dto.request.ResendVerificationRequest;
import com.monitoring.auth.dto.request.ResetPasswordRequest;
import com.monitoring.auth.dto.request.VerifyEmailRequest;
import com.monitoring.auth.exception.EmailAlreadyUsedException;
import com.monitoring.auth.exception.EmailNotVerifiedException;
import com.monitoring.auth.exception.InvalidCredentialsException;
import com.monitoring.auth.exception.InvalidTokenException;
import com.monitoring.auth.service.AuthService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.BDDMockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@ContextConfiguration(classes = AuthTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    private RegisterRequest validRegisterRequest;
    private LoginRequest validLoginRequest;
    private AuthResponse mockAuthResponse;
    private MessageResponse mockMessageResponse;

    @BeforeEach
    void setUp() {
        validRegisterRequest = new RegisterRequest();
        validRegisterRequest.setUsername("testuser");
        validRegisterRequest.setEmail("test@example.com");
        validRegisterRequest.setPassword("password123");

        validLoginRequest = new LoginRequest();
        validLoginRequest.setUsername("testuser");
        validLoginRequest.setPassword("password123");

        mockAuthResponse = new AuthResponse("mock-jwt-token");
        mockMessageResponse = new MessageResponse("mock-message");
    }

    @Test
    void register_success() throws Exception {
        BDDMockito.given(authService.register(ArgumentMatchers.any(RegisterRequest.class)))
                .willReturn(mockMessageResponse);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("mock-message"));

        BDDMockito.then(authService).should().register(ArgumentMatchers.any(RegisterRequest.class));
    }

    @Test
    void register_duplicateAccount() throws Exception {
        BDDMockito.given(authService.register(ArgumentMatchers.any(RegisterRequest.class)))
                .willThrow(new EmailAlreadyUsedException("Email already exists"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_validationError() throws Exception {
        RegisterRequest invalid = new RegisterRequest();
        invalid.setUsername("");
        invalid.setEmail("not-an-email");
        invalid.setPassword("short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        BDDMockito.then(authService).shouldHaveNoInteractions();
    }

    @Test
    void login_success() throws Exception {
        BDDMockito.given(authService.login(ArgumentMatchers.any(LoginRequest.class)))
                .willReturn(mockAuthResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-jwt-token"));

        BDDMockito.then(authService).should().login(ArgumentMatchers.any(LoginRequest.class));
    }

    @Test
    void login_invalidPassword() throws Exception {
        BDDMockito.given(authService.login(ArgumentMatchers.any(LoginRequest.class)))
                .willThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_emailNotVerified() throws Exception {
        BDDMockito.given(authService.login(ArgumentMatchers.any(LoginRequest.class)))
                .willThrow(new EmailNotVerifiedException("Email not verified"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void verifyEmail_success() throws Exception {
        VerifyEmailRequest request = new VerifyEmailRequest();
        request.setToken("raw-token");
        BDDMockito.given(authService.verifyEmail("raw-token")).willReturn(mockMessageResponse);

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("mock-message"));
    }

    @Test
    void verifyEmail_invalidToken() throws Exception {
        VerifyEmailRequest request = new VerifyEmailRequest();
        request.setToken("raw-token");
        BDDMockito.given(authService.verifyEmail(ArgumentMatchers.anyString()))
                .willThrow(new InvalidTokenException("Invalid or expired token"));

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resendVerification_success() throws Exception {
        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail("test@example.com");
        BDDMockito.given(authService.resendVerification("test@example.com")).willReturn(mockMessageResponse);

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("mock-message"));
    }

    @Test
    void forgotPassword_success() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("test@example.com");
        BDDMockito.given(authService.forgotPassword("test@example.com")).willReturn(mockMessageResponse);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("mock-message"));
    }

    @Test
    void resetPassword_success() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("raw-token");
        request.setNewPassword("newsecret1");
        BDDMockito.given(authService.resetPassword("raw-token", "newsecret1")).willReturn(mockMessageResponse);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("mock-message"));
    }

    @Test
    void confirmEmailChange_success() throws Exception {
        VerifyEmailRequest request = new VerifyEmailRequest();
        request.setToken("raw-token");
        BDDMockito.given(authService.confirmEmailChange("raw-token")).willReturn(mockMessageResponse);

        mockMvc.perform(post("/api/auth/email-change/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("mock-message"));
    }

    @Test
    void confirmEmailChange_invalidToken() throws Exception {
        VerifyEmailRequest request = new VerifyEmailRequest();
        request.setToken("raw-token");
        BDDMockito.given(authService.confirmEmailChange(ArgumentMatchers.anyString()))
                .willThrow(new InvalidTokenException("Invalid or expired token"));

        mockMvc.perform(post("/api/auth/email-change/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
