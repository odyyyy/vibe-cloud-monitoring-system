package com.monitoring.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.auth.dto.response.MessageResponse;
import com.monitoring.auth.dto.request.UpdateEmailRequest;
import com.monitoring.auth.dto.request.UpdateUsernameRequest;
import com.monitoring.auth.dto.response.UserProfileResponse;
import com.monitoring.auth.exception.EmailAlreadyUsedException;
import com.monitoring.auth.exception.InvalidCredentialsException;
import com.monitoring.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileControllerTest {

    private static final Long USER_ID = 7L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = BDDMockito.mock(AuthService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProfileController(authService))
                .setControllerAdvice(new AuthExceptionHandler())
                .build();
    }

    private Principal principal() {
        return new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, List.of());
    }

    @Test
    void updateUsername_success() throws Exception {
        UpdateUsernameRequest request = new UpdateUsernameRequest();
        request.setCurrentPassword("supersecret");
        request.setUsername("grace");
        BDDMockito.given(authService.updateUsername(USER_ID, "supersecret", "grace"))
                .willReturn(new UserProfileResponse(USER_ID, "grace", "ada@example.com", null));

        mockMvc.perform(put("/api/profile/username")
                        .principal(principal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("grace"));
    }

    @Test
    void updateUsername_validationError() throws Exception {
        UpdateUsernameRequest request = new UpdateUsernameRequest();
        request.setCurrentPassword("");
        request.setUsername("no");

        mockMvc.perform(put("/api/profile/username")
                        .principal(principal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        BDDMockito.then(authService).shouldHaveNoInteractions();
    }

    @Test
    void updateUsername_wrongPassword() throws Exception {
        UpdateUsernameRequest request = new UpdateUsernameRequest();
        request.setCurrentPassword("wrong");
        request.setUsername("grace");
        BDDMockito.given(authService.updateUsername(anyLong(), anyString(), anyString()))
                .willThrow(new InvalidCredentialsException());

        mockMvc.perform(put("/api/profile/username")
                        .principal(principal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateUsername_taken() throws Exception {
        UpdateUsernameRequest request = new UpdateUsernameRequest();
        request.setCurrentPassword("supersecret");
        request.setUsername("grace");
        BDDMockito.given(authService.updateUsername(anyLong(), anyString(), anyString()))
                .willThrow(new EmailAlreadyUsedException("Username already exists"));

        mockMvc.perform(put("/api/profile/username")
                        .principal(principal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username already exists"));
    }

    @Test
    void updateUsername_unauthenticated() throws Exception {
        UpdateUsernameRequest request = new UpdateUsernameRequest();
        request.setCurrentPassword("supersecret");
        request.setUsername("grace");

        mockMvc.perform(put("/api/profile/username")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usernameAvailable_returnsAvailability() throws Exception {
        BDDMockito.given(authService.isUsernameAvailable("grace")).willReturn(true);

        mockMvc.perform(get("/api/profile/username-available")
                        .principal(principal())
                        .param("username", "grace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void usernameAvailable_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/profile/username-available").param("username", "grace"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestEmailChange_success() throws Exception {
        UpdateEmailRequest request = new UpdateEmailRequest();
        request.setCurrentPassword("supersecret");
        request.setNewEmail("new@example.com");
        BDDMockito.given(authService.requestEmailChange(eq(USER_ID), eq("supersecret"), eq("new@example.com")))
                .willReturn(new MessageResponse("Confirmation link sent to new@example.com."));

        mockMvc.perform(put("/api/profile/email")
                        .principal(principal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Confirmation link sent to new@example.com."));
    }

    @Test
    void requestEmailChange_taken() throws Exception {
        UpdateEmailRequest request = new UpdateEmailRequest();
        request.setCurrentPassword("supersecret");
        request.setNewEmail("taken@example.com");
        BDDMockito.given(authService.requestEmailChange(anyLong(), anyString(), anyString()))
                .willThrow(new EmailAlreadyUsedException("Email already exists"));

        mockMvc.perform(put("/api/profile/email")
                        .principal(principal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void requestEmailChange_invalidEmail() throws Exception {
        UpdateEmailRequest request = new UpdateEmailRequest();
        request.setCurrentPassword("supersecret");
        request.setNewEmail("not-an-email");

        mockMvc.perform(put("/api/profile/email")
                        .principal(principal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        BDDMockito.then(authService).shouldHaveNoInteractions();
    }
}
