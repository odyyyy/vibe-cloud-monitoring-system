package com.monitoring.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.auth.dto.AuthResponse;
import com.monitoring.auth.dto.LoginRequest;
import com.monitoring.auth.dto.RegisterRequest;
import com.monitoring.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
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
    }

    @Test
    void register_success() throws Exception {
        // Given
        BDDMockito.given(authService.register(ArgumentMatchers.any(RegisterRequest.class)))
                .willReturn(mockAuthResponse);

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").value("mock-jwt-token"));

        // Verify
        BDDMockito.then(authService).should().register(ArgumentMatchers.any(RegisterRequest.class));
    }

    @Test
    void register_serviceError() throws Exception {
        // Given
        BDDMockito.given(authService.register(ArgumentMatchers.any(RegisterRequest.class)))
                .willThrow(new RuntimeException("Username already exists"));

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isInternalServerError());

        // Verify
        BDDMockito.then(authService).should().register(ArgumentMatchers.any(RegisterRequest.class));
    }

    @Test
    void login_success() throws Exception {
        // Given
        BDDMockito.given(authService.login(ArgumentMatchers.any(LoginRequest.class)))
                .willReturn(mockAuthResponse);

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").value("mock-jwt-token"));

        // Verify
        BDDMockito.then(authService).should().login(ArgumentMatchers.any(LoginRequest.class));
    }

    @Test
    void login_invalidPassword() throws Exception {
        // Given
        BDDMockito.given(authService.login(ArgumentMatchers.any(LoginRequest.class)))
                .willThrow(new RuntimeException("Invalid password"));

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isUnauthorized());

        // Verify
        BDDMockito.then(authService).should().login(ArgumentMatchers.any(LoginRequest.class));
    }

    @Test
    void login_userNotFound() throws Exception {
        // Given
        BDDMockito.given(authService.login(ArgumentMatchers.any(LoginRequest.class)))
                .willThrow(new RuntimeException("User not found"));

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isNotFound());

        // Verify
        BDDMockito.then(authService).should().login(ArgumentMatchers.any(LoginRequest.class));
    }
}