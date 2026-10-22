package com.monitoring.retention.service;

import com.monitoring.retention.config.RetentionProperties;
import com.monitoring.retention.dto.request.UpdateRetentionPolicyRequest;
import com.monitoring.retention.dto.response.RetentionPolicyResponse;
import com.monitoring.retention.entity.RetentionPolicy;
import com.monitoring.retention.exception.InvalidRetentionPolicyException;
import com.monitoring.retention.repository.RetentionPolicyRepository;
import com.monitoring.retention.repository.RetentionPurgeRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetentionPolicyServiceTest {

    private static final Long ORGANIZATION_ID = 7L;
    private static final Long USER_ID = 42L;

    private RetentionPolicyRepository policyRepository;
    private RetentionPolicyService service;

    @BeforeEach
    void setUp() {
        policyRepository = Mockito.mock(RetentionPolicyRepository.class);
        RetentionPurgeRunRepository runRepository = Mockito.mock(RetentionPurgeRunRepository.class);
        RetentionProperties properties = new RetentionProperties();
        service = new RetentionPolicyService(policyRepository, runRepository, properties);
    }

    @Test
    void get_returnsConfiguredDefaultsWhenNoPolicyExists() {
        when(policyRepository.findByOrganizationId(ORGANIZATION_ID)).thenReturn(Optional.empty());

        RetentionPolicyResponse response = service.get(ORGANIZATION_ID);

        assertEquals(ORGANIZATION_ID, response.organizationId());
        assertEquals(14, response.logsRetentionDays());
        assertEquals(30, response.metricsRetentionDays());
        assertEquals(7, response.tracesRetentionDays());
        assertNull(response.updatedAt());
        assertNull(response.updatedBy());
        assertEquals(90, response.maxLogsRetentionDays());
        assertEquals(365, response.maxMetricsRetentionDays());
        assertEquals(30, response.maxTracesRetentionDays());
    }

    @Test
    void update_persistsValuesAndReturnsThem() {
        when(policyRepository.findByOrganizationId(ORGANIZATION_ID)).thenReturn(Optional.empty());
        when(policyRepository.save(any(RetentionPolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RetentionPolicyResponse response = service.update(ORGANIZATION_ID, USER_ID,
                new UpdateRetentionPolicyRequest(30, 120, 20));

        assertEquals(30, response.logsRetentionDays());
        assertEquals(120, response.metricsRetentionDays());
        assertEquals(20, response.tracesRetentionDays());
        assertEquals(USER_ID, response.updatedBy());

        ArgumentCaptor<RetentionPolicy> captor = ArgumentCaptor.forClass(RetentionPolicy.class);
        verify(policyRepository).save(captor.capture());
        RetentionPolicy saved = captor.getValue();
        assertEquals(ORGANIZATION_ID, saved.getOrganizationId());
        assertEquals(30, saved.getLogsRetentionDays());
        assertEquals(120, saved.getMetricsRetentionDays());
        assertEquals(20, saved.getTracesRetentionDays());
        assertEquals(USER_ID, saved.getUpdatedBy());
        assertEquals(Instant.now().getEpochSecond(), saved.getUpdatedAt().getEpochSecond(), 5);
    }

    @Test
    void update_rejectsValuesAboveTheHardLimit() {
        InvalidRetentionPolicyException ex = assertThrows(InvalidRetentionPolicyException.class,
                () -> service.update(ORGANIZATION_ID, USER_ID,
                        new UpdateRetentionPolicyRequest(14, 366, 7)));

        assertEquals("Metrics retention must not exceed 365 days", ex.getMessage());
        verify(policyRepository, Mockito.never()).save(any());
    }

    @Test
    void update_rejectsNonPositiveValues() {
        assertThrows(InvalidRetentionPolicyException.class,
                () -> service.update(ORGANIZATION_ID, USER_ID,
                        new UpdateRetentionPolicyRequest(0, 30, 7)));
    }

    @Test
    void effective_usesStoredPolicyWhenPresent() {
        RetentionPolicy policy = RetentionPolicy.builder()
                .organizationId(ORGANIZATION_ID)
                .logsRetentionDays(5)
                .metricsRetentionDays(10)
                .tracesRetentionDays(3)
                .updatedAt(Instant.now())
                .build();
        when(policyRepository.findByOrganizationId(ORGANIZATION_ID)).thenReturn(Optional.of(policy));

        EffectiveRetention effective = service.effective(ORGANIZATION_ID);

        assertEquals(new EffectiveRetention(5, 10, 3), effective);
    }
}
