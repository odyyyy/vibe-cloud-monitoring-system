package com.monitoring.api.controller;

import com.monitoring.api.exception.ApiExceptionHandler;
import com.monitoring.storage.dto.ApplicationCreateRequest;
import com.monitoring.storage.dto.ApplicationWithKeyResponse;
import com.monitoring.storage.entity.Application;
import com.monitoring.storage.exception.ApplicationAccessDeniedException;
import com.monitoring.storage.exception.ApplicationNotFoundException;
import com.monitoring.storage.service.ApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApplicationControllerTest {

    private static final Long USER_ID = 42L;

    private ApplicationService applicationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationService = Mockito.mock(ApplicationService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ApplicationController(applicationService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Application sampleApp() {
        return Application.builder()
                .id(10L)
                .name("my-app")
                .apiKeyHash("hashed-key")
                .ownerId(USER_ID)
                .build();
    }

    private ApplicationWithKeyResponse sampleAppWithKey() {
        return new ApplicationWithKeyResponse(10L, "my-app", "app_abc", null);
    }

    @Test
    void listApplications_returnsOwnAppsWithoutApiKey() throws Exception {
        when(applicationService.findByOwnerId(USER_ID)).thenReturn(List.of(sampleApp()));

        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].name").value("my-app"))
                .andExpect(jsonPath("$[0].apiKey").doesNotExist());
    }

    @Test
    void createApplication_valid_returnsCreatedWithLocationAndKey() throws Exception {
        when(applicationService.createApplication(any(ApplicationCreateRequest.class), eq(USER_ID)))
                .thenReturn(sampleAppWithKey());

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"my-app\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/applications/10"))
                .andExpect(jsonPath("$.apiKey").value("app_abc"));
    }

    @Test
    void createApplication_blankName_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
        verify(applicationService, Mockito.never()).createApplication(any(), any());
    }

    @Test
    void getApplication_notFound_returns404() throws Exception {
        when(applicationService.getOwnedApplication(99L, USER_ID))
                .thenThrow(new ApplicationNotFoundException(99L));

        mockMvc.perform(get("/api/applications/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getApplication_wrongOwner_returns403() throws Exception {
        when(applicationService.getOwnedApplication(10L, USER_ID))
                .thenThrow(new ApplicationAccessDeniedException());

        mockMvc.perform(get("/api/applications/10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void deleteApplication_success_returns204() throws Exception {
        mockMvc.perform(delete("/api/applications/10"))
                .andExpect(status().isNoContent());
        verify(applicationService).deleteApplication(10L, USER_ID);
    }

    @Test
    void deleteApplication_wrongOwner_returns403() throws Exception {
        Mockito.doThrow(new ApplicationAccessDeniedException())
                .when(applicationService).deleteApplication(10L, USER_ID);

        mockMvc.perform(delete("/api/applications/10"))
                .andExpect(status().isForbidden());
    }

    @Test
    void regenerateApiKey_success_returnsNewKey() throws Exception {
        when(applicationService.regenerateApiKey(10L, USER_ID)).thenReturn(sampleAppWithKey());

        mockMvc.perform(post("/api/applications/10/regenerate-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").value("app_abc"));
    }
}
