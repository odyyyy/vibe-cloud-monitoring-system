package com.monitoring.auth.controller;

import com.monitoring.auth.dto.pat.PatSummary;
import com.monitoring.auth.entity.PersonalAccessToken;
import com.monitoring.auth.exception.PatNotFoundException;
import com.monitoring.auth.service.PersonalAccessTokenService;
import com.monitoring.organization.tenant.CurrentTenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PatControllerTest {

    private static final long USER_ID = 7L;
    private static final long ORG_ID = 10L;
    private static final long TOKEN_ID = 42L;

    private PersonalAccessTokenService patService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        patService = Mockito.mock(PersonalAccessTokenService.class);
        CurrentTenantService tenantService = Mockito.mock(CurrentTenantService.class);
        given(tenantService.requireUserId()).willReturn(USER_ID);
        given(tenantService.requireOrganizationId()).willReturn(ORG_ID);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PatController(patService, tenantService))
                .setControllerAdvice(new AuthExceptionHandler())
                .build();
    }

    @Test
    void createReturnsTheRawTokenOnce() throws Exception {
        PersonalAccessToken token = PersonalAccessToken.builder()
                .id(TOKEN_ID)
                .userId(USER_ID)
                .organizationId(ORG_ID)
                .name("ci")
                .tokenHash("hash")
                .expiresAt(Instant.parse("2026-12-01T00:00:00Z"))
                .scopes(Set.of("read:metrics"))
                .build();
        given(patService.generateToken(eq(USER_ID), eq(ORG_ID), eq("ci"), any(), anyInt()))
                .willReturn(new PersonalAccessTokenService.GeneratedToken("pat_raw", token));

        mockMvc.perform(post("/api/v1/settings/pats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ci\",\"expiresDays\":30,\"scopes\":[\"read:metrics\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rawToken").value("pat_raw"))
                .andExpect(jsonPath("$.tokenId").value(TOKEN_ID))
                .andExpect(jsonPath("$.name").value("ci"))
                .andExpect(jsonPath("$.scopes[0]").value("read:metrics"))
                .andExpect(jsonPath("$.tokenHash").doesNotExist());

        then(patService).should().generateToken(eq(USER_ID), eq(ORG_ID), eq("ci"),
                eq(Set.of("read:metrics")), eq(30));
    }

    @Test
    void createRejectsEmptyNameAndMissingScopes() throws Exception {
        mockMvc.perform(post("/api/v1/settings/pats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"expiresDays\":30,\"scopes\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsInvalidExpiration() throws Exception {
        mockMvc.perform(post("/api/v1/settings/pats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ci\",\"expiresDays\":0,\"scopes\":[\"read:metrics\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listNeverExposesSecrets() throws Exception {
        given(patService.listActiveTokens(USER_ID, ORG_ID)).willReturn(List.of(
                new PatSummary(TOKEN_ID, "ci", Instant.parse("2026-12-01T00:00:00Z"),
                        null, List.of("read:metrics"))));

        mockMvc.perform(get("/api/v1/settings/pats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaries[0].id").value(TOKEN_ID))
                .andExpect(jsonPath("$.summaries[0].name").value("ci"))
                .andExpect(jsonPath("$.summaries[0].tokenHash").doesNotExist())
                .andExpect(jsonPath("$.summaries[0].rawToken").doesNotExist());
    }

    @Test
    void revokeReturnsSuccess() throws Exception {
        mockMvc.perform(delete("/api/v1/settings/pats/{id}", TOKEN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenId").value(TOKEN_ID));

        verify(patService).revokeToken(TOKEN_ID, USER_ID, ORG_ID);
    }

    @Test
    void revokeUnknownTokenReturnsNotFound() throws Exception {
        Mockito.doThrow(new PatNotFoundException())
                .when(patService).revokeToken(anyLong(), anyLong(), anyLong());

        mockMvc.perform(delete("/api/v1/settings/pats/{id}", TOKEN_ID))
                .andExpect(status().isNotFound());
    }
}
