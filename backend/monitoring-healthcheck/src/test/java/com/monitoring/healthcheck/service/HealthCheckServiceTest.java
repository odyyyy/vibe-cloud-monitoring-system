package com.monitoring.healthcheck.service;

import com.monitoring.healthcheck.dto.request.CreateHealthCheckRequest;
import com.monitoring.healthcheck.dto.response.HealthCheckResponse;
import com.monitoring.healthcheck.dto.request.UpdateHealthCheckRequest;
import com.monitoring.healthcheck.dto.SyntheticApiStep;
import com.monitoring.healthcheck.entity.HealthCheckConfig;
import com.monitoring.healthcheck.entity.HealthCheckMethod;
import com.monitoring.healthcheck.entity.HealthCheckType;
import com.monitoring.healthcheck.entity.SyntheticHttpMethod;
import com.monitoring.healthcheck.exception.HealthCheckAccessDeniedException;
import com.monitoring.healthcheck.exception.HealthCheckNotFoundException;
import com.monitoring.healthcheck.exception.InvalidTargetUrlException;
import com.monitoring.healthcheck.repository.HealthCheckConfigRepository;
import com.monitoring.healthcheck.repository.HealthCheckResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealthCheckServiceTest {

    private static final String PUBLIC_URL = "http://8.8.8.8/health";

    private HealthCheckConfigRepository configRepository;
    private HealthCheckResultRepository resultRepository;
    private HealthCheckService service;

    @BeforeEach
    void setUp() {
        configRepository = Mockito.mock(HealthCheckConfigRepository.class);
        resultRepository = Mockito.mock(HealthCheckResultRepository.class);
        SsrfGuard ssrfGuard = new SsrfGuard();
        service = new HealthCheckService(
                configRepository,
                resultRepository,
                ssrfGuard,
                new SyntheticCheckDefinitionValidator(ssrfGuard),
                new SyntheticCheckCodec(new ObjectMapper()));
        when(configRepository.save(any(HealthCheckConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private HealthCheckConfig existingConfig(long organizationId) {
        return HealthCheckConfig.builder()
                .id(7L)
                .organizationId(organizationId)
                .name("API")
                .targetUrl(PUBLIC_URL)
                .method(HealthCheckMethod.GET)
                .expectedStatusCode(200)
                .intervalSeconds(30)
                .active(true)
                .build();
    }

    @Test
    void createAppliesDefaultsWhenOptionalFieldsMissing() {
        CreateHealthCheckRequest request = CreateHealthCheckRequest.builder()
                .name("  API health  ")
                .targetUrl(PUBLIC_URL)
                .method(HealthCheckMethod.GET)
                .build();

        HealthCheckResponse response = service.create(1L, request);

        assertEquals("API health", response.getName());
        assertEquals(HealthCheckService.DEFAULT_EXPECTED_STATUS_CODE, response.getExpectedStatusCode());
        assertEquals(HealthCheckService.DEFAULT_INTERVAL_SECONDS, response.getIntervalSeconds());
        assertTrue(response.isActive());
    }

    @Test
    void createRespectsExplicitInactiveFlag() {
        CreateHealthCheckRequest request = CreateHealthCheckRequest.builder()
                .name("API")
                .targetUrl(PUBLIC_URL)
                .method(HealthCheckMethod.POST)
                .expectedStatusCode(204)
                .intervalSeconds(120)
                .active(false)
                .build();

        HealthCheckResponse response = service.create(1L, request);

        assertEquals(HealthCheckMethod.POST, response.getMethod());
        assertEquals(204, response.getExpectedStatusCode());
        assertEquals(120, response.getIntervalSeconds());
        assertFalse(response.isActive());
    }

    @Test
    void createRejectsNonPublicTarget() {
        CreateHealthCheckRequest request = CreateHealthCheckRequest.builder()
                .name("API")
                .targetUrl("http://127.0.0.1/health")
                .method(HealthCheckMethod.GET)
                .build();

        assertThrows(InvalidTargetUrlException.class, () -> service.create(1L, request));
        verify(configRepository, never()).save(any());
    }

    @Test
    void updateAppliesOnlyProvidedFields() {
        HealthCheckConfig config = existingConfig(1L);
        when(configRepository.findById(7L)).thenReturn(Optional.of(config));

        HealthCheckResponse response = service.update(1L, 7L,
                UpdateHealthCheckRequest.builder().name("Renamed").build());

        assertEquals("Renamed", response.getName());
        assertEquals(PUBLIC_URL, response.getTargetUrl());
        assertEquals(30, response.getIntervalSeconds());
        assertEquals(HealthCheckMethod.GET, response.getMethod());
    }

    @Test
    void updateCanToggleActiveFlag() {
        HealthCheckConfig config = existingConfig(1L);
        when(configRepository.findById(7L)).thenReturn(Optional.of(config));

        HealthCheckResponse response = service.update(1L, 7L,
                UpdateHealthCheckRequest.builder().active(false).build());

        assertFalse(response.isActive());
        assertEquals("API", response.getName());
    }

    @Test
    void updateRevalidatesChangedTarget() {
        HealthCheckConfig config = existingConfig(1L);
        when(configRepository.findById(7L)).thenReturn(Optional.of(config));

        assertThrows(InvalidTargetUrlException.class, () -> service.update(1L, 7L,
                UpdateHealthCheckRequest.builder().targetUrl("http://169.254.169.254/").build()));
    }

    @Test
    void updateMissingConfigRaisesNotFound() {
        when(configRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(HealthCheckNotFoundException.class, () -> service.update(1L, 99L,
                UpdateHealthCheckRequest.builder().name("x").build()));
    }

    @Test
    void foreignConfigRaisesAccessDenied() {
        HealthCheckConfig config = existingConfig(1L);
        when(configRepository.findById(7L)).thenReturn(Optional.of(config));

        assertThrows(HealthCheckAccessDeniedException.class, () -> service.update(2L, 7L,
                UpdateHealthCheckRequest.builder().name("x").build()));
        assertThrows(HealthCheckAccessDeniedException.class, () -> service.listResults(2L, 7L, 10));
    }

    @Test
    void deleteRemovesResultsThenConfig() {
        HealthCheckConfig config = existingConfig(1L);
        when(configRepository.findById(7L)).thenReturn(Optional.of(config));

        service.delete(1L, 7L);

        verify(resultRepository).deleteByConfig_Id(7L);
        verify(configRepository).delete(config);
    }

    @Test
    void listResultsClampsLimit() {
        HealthCheckConfig config = existingConfig(1L);
        when(configRepository.findById(7L)).thenReturn(Optional.of(config));

        service.listResults(1L, 7L, 100_000);
        service.listResults(1L, 7L, -5);
        service.listResults(1L, 7L, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(resultRepository, Mockito.times(3))
                .findByConfig_IdOrderByCheckedAtDesc(Mockito.eq(7L), captor.capture());

        assertEquals(HealthCheckService.MAX_RESULTS_LIMIT, captor.getAllValues().get(0).getPageSize());
        assertEquals(1, captor.getAllValues().get(1).getPageSize());
        assertEquals(HealthCheckService.DEFAULT_RESULTS_LIMIT, captor.getAllValues().get(2).getPageSize());
    }

    private SyntheticApiStep loginStep(String url) {
        return SyntheticApiStep.builder()
                .name("Login")
                .method(SyntheticHttpMethod.POST)
                .url(url)
                .body("{\"user\":\"demo\"}")
                .extractVariables(Map.of("token", "jsonpath:$.access_token"))
                .build();
    }

    @Test
    void createMultiStepStoresWorkflowAndClearsTargetUrl() {
        CreateHealthCheckRequest request = CreateHealthCheckRequest.builder()
                .name("Login flow")
                .type(HealthCheckType.MULTI_STEP_API)
                .intervalSeconds(60)
                .steps(List.of(loginStep(PUBLIC_URL)))
                .build();

        HealthCheckResponse response = service.create(1L, request);

        assertEquals(HealthCheckType.MULTI_STEP_API, response.getType());
        assertEquals(1, response.getSteps().size());
        assertEquals("Login", response.getSteps().get(0).getName());
        assertEquals(null, response.getTargetUrl());

        ArgumentCaptor<HealthCheckConfig> captor = ArgumentCaptor.forClass(HealthCheckConfig.class);
        verify(configRepository).save(captor.capture());
        assertTrue(captor.getValue().getStepsJson().contains("jsonpath:$.access_token"));
    }

    @Test
    void createMultiStepRejectsMetadataStepUrl() {
        CreateHealthCheckRequest request = CreateHealthCheckRequest.builder()
                .name("Metadata")
                .type(HealthCheckType.MULTI_STEP_API)
                .intervalSeconds(60)
                .steps(List.of(loginStep("http://169.254.169.254/latest/meta-data/")))
                .build();

        assertThrows(InvalidTargetUrlException.class, () -> service.create(1L, request));
        verify(configRepository, never()).save(any());
    }

    @Test
    void createSingleCheckRejectsSteps() {
        CreateHealthCheckRequest request = CreateHealthCheckRequest.builder()
                .name("API")
                .targetUrl(PUBLIC_URL)
                .method(HealthCheckMethod.GET)
                .intervalSeconds(60)
                .steps(List.of(loginStep(PUBLIC_URL)))
                .build();

        assertThrows(InvalidTargetUrlException.class, () -> service.create(1L, request));
        verify(configRepository, never()).save(any());
    }

    @Test
    void switchingMultiStepBackToSingleRequiresTargetUrl() {
        HealthCheckConfig config = HealthCheckConfig.builder()
                .id(7L)
                .organizationId(1L)
                .name("Flow")
                .type(HealthCheckType.MULTI_STEP_API)
                .targetUrl(null)
                .stepsJson("[]")
                .method(HealthCheckMethod.GET)
                .expectedStatusCode(200)
                .intervalSeconds(60)
                .active(true)
                .build();
        when(configRepository.findById(7L)).thenReturn(Optional.of(config));

        assertThrows(InvalidTargetUrlException.class, () -> service.update(1L, 7L,
                UpdateHealthCheckRequest.builder().type(HealthCheckType.SINGLE_HTTP).build()));
    }
}
