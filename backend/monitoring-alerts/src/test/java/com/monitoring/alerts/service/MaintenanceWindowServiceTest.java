package com.monitoring.alerts.service;

import com.monitoring.alerts.dto.request.CreateMaintenanceWindowRequest;
import com.monitoring.alerts.dto.request.UpdateMaintenanceWindowRequest;
import com.monitoring.alerts.dto.response.MaintenanceWindowResponse;
import com.monitoring.alerts.entity.AlertRule;
import com.monitoring.alerts.entity.MaintenanceScopeType;
import com.monitoring.alerts.entity.MaintenanceWindow;
import com.monitoring.alerts.exception.InvalidMaintenanceWindowException;
import com.monitoring.alerts.exception.MaintenanceWindowAccessDeniedException;
import com.monitoring.alerts.exception.MaintenanceWindowNotFoundException;
import com.monitoring.alerts.repository.AlertRuleRepository;
import com.monitoring.alerts.repository.MaintenanceWindowRepository;
import com.monitoring.apps.exception.ApplicationAccessDeniedException;
import com.monitoring.apps.service.ApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaintenanceWindowServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long OTHER_ORG_ID = 2L;
    private static final Long APP_ID = 7L;
    private static final Long RULE_ID = 11L;

    private MaintenanceWindowRepository windowRepository;
    private AlertRuleRepository ruleRepository;
    private ApplicationService applicationService;
    private MaintenanceWindowService service;

    @BeforeEach
    void setUp() {
        windowRepository = Mockito.mock(MaintenanceWindowRepository.class);
        ruleRepository = Mockito.mock(AlertRuleRepository.class);
        applicationService = Mockito.mock(ApplicationService.class);
        service = new MaintenanceWindowService(windowRepository, ruleRepository, applicationService);
        when(windowRepository.save(any(MaintenanceWindow.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private MaintenanceWindow window(MaintenanceScopeType type, Long scopeId, boolean active) {
        return MaintenanceWindow.builder()
                .id(1L)
                .organizationId(ORG_ID)
                .name("Deploy")
                .scopeType(type)
                .scopeId(scopeId)
                .startTime(Instant.parse("2026-10-21T10:00:00Z"))
                .endTime(Instant.parse("2026-10-21T12:00:00Z"))
                .active(active)
                .build();
    }

    private CreateMaintenanceWindowRequest createRequest(
            MaintenanceScopeType type, Long scopeId, Instant start, Instant end) {
        return CreateMaintenanceWindowRequest.builder()
                .name("Deploy")
                .scopeType(type)
                .scopeId(scopeId)
                .startTime(start)
                .endTime(end)
                .build();
    }

    @Test
    void createGlobalWindowPersistsWithoutScopeId() {
        Instant now = Instant.now();

        MaintenanceWindowResponse created = service.create(ORG_ID,
                createRequest(MaintenanceScopeType.GLOBAL, null, now, now.plusSeconds(7200)));

        assertEquals(MaintenanceScopeType.GLOBAL, created.getScopeType());
        assertEquals(null, created.getScopeId());
        assertTrue(created.isActive());
    }

    @Test
    void createRejectsStartMoreThanOneHourInThePast() {
        Instant now = Instant.now();

        assertThrows(InvalidMaintenanceWindowException.class, () -> service.create(ORG_ID,
                createRequest(MaintenanceScopeType.GLOBAL, null,
                        now.minusSeconds(3601), now.plusSeconds(3600))));
        verify(windowRepository, never()).save(any());
    }

    @Test
    void createAllowsSlightBackdating() {
        Instant now = Instant.now();

        MaintenanceWindowResponse created = service.create(ORG_ID,
                createRequest(MaintenanceScopeType.GLOBAL, null,
                        now.minusSeconds(1800), now.plusSeconds(3600)));

        assertEquals(MaintenanceScopeType.GLOBAL, created.getScopeType());
    }

    @Test
    void createRejectsEndNotAfterStartOrInThePast() {
        Instant now = Instant.now();

        assertThrows(InvalidMaintenanceWindowException.class, () -> service.create(ORG_ID,
                createRequest(MaintenanceScopeType.GLOBAL, null,
                        now.plusSeconds(3600), now.plusSeconds(3600))));
        assertThrows(InvalidMaintenanceWindowException.class, () -> service.create(ORG_ID,
                createRequest(MaintenanceScopeType.GLOBAL, null,
                        now.minusSeconds(1800), now.minusSeconds(60))));
    }

    @Test
    void createApplicationWindowRequiresOwnedScope() {
        Instant now = Instant.now();
        assertThrows(InvalidMaintenanceWindowException.class, () -> service.create(ORG_ID,
                createRequest(MaintenanceScopeType.APPLICATION, null, now, now.plusSeconds(3600))));

        when(applicationService.getOwnedApplication(APP_ID, ORG_ID))
                .thenThrow(new ApplicationAccessDeniedException());

        assertThrows(InvalidMaintenanceWindowException.class, () -> service.create(ORG_ID,
                createRequest(MaintenanceScopeType.APPLICATION, APP_ID, now, now.plusSeconds(3600))));
    }

    @Test
    void createRuleWindowRequiresOwnedRule() {
        Instant now = Instant.now();
        when(ruleRepository.findByIdAndOrganizationId(RULE_ID, ORG_ID)).thenReturn(Optional.empty());

        assertThrows(InvalidMaintenanceWindowException.class, () -> service.create(ORG_ID,
                createRequest(MaintenanceScopeType.SPECIFIC_RULE, RULE_ID, now, now.plusSeconds(3600))));

        when(ruleRepository.findByIdAndOrganizationId(RULE_ID, ORG_ID))
                .thenReturn(Optional.of(AlertRule.builder().id(RULE_ID).organizationId(ORG_ID).build()));

        MaintenanceWindowResponse created = service.create(ORG_ID,
                createRequest(MaintenanceScopeType.SPECIFIC_RULE, RULE_ID, now, now.plusSeconds(3600)));

        assertEquals(RULE_ID, created.getScopeId());
    }

    @Test
    void globalWindowCannotCarryScopeId() {
        Instant now = Instant.now();

        assertThrows(InvalidMaintenanceWindowException.class, () -> service.create(ORG_ID,
                createRequest(MaintenanceScopeType.GLOBAL, APP_ID, now, now.plusSeconds(3600))));
    }

    @Test
    void updateCanEndWindowEarlyWithoutChangingTimes() {
        MaintenanceWindow existing = window(MaintenanceScopeType.GLOBAL, null, true);
        when(windowRepository.findById(1L)).thenReturn(Optional.of(existing));

        MaintenanceWindowResponse updated = service.update(ORG_ID, 1L,
                UpdateMaintenanceWindowRequest.builder().active(false).build());

        assertFalse(updated.isActive());
        verify(windowRepository).save(existing);
    }

    @Test
    void updateCanRenameAnActiveWindowWithoutRevalidatingItsPastStart() {
        MaintenanceWindow existing = window(MaintenanceScopeType.GLOBAL, null, true);
        existing.setStartTime(Instant.now().minusSeconds(7200));
        existing.setEndTime(Instant.now().plusSeconds(3600));
        when(windowRepository.findById(1L)).thenReturn(Optional.of(existing));

        MaintenanceWindowResponse updated = service.update(ORG_ID, 1L,
                UpdateMaintenanceWindowRequest.builder()
                        .name("Renamed")
                        .startTime(existing.getStartTime())
                        .build());

        assertEquals("Renamed", updated.getName());
        assertEquals(existing.getStartTime(), updated.getStartTime());
        verify(windowRepository).save(existing);
    }

    @Test
    void updateRejectsAChangedStartMoreThanOneHourInThePast() {
        MaintenanceWindow existing = window(MaintenanceScopeType.GLOBAL, null, true);
        when(windowRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(InvalidMaintenanceWindowException.class, () -> service.update(ORG_ID, 1L,
                UpdateMaintenanceWindowRequest.builder()
                        .startTime(Instant.now().minusSeconds(7200))
                        .build()));
    }

    @Test
    void updateRejectsForeignWindow() {
        when(windowRepository.findById(1L))
                .thenReturn(Optional.of(window(MaintenanceScopeType.GLOBAL, null, true)));

        assertThrows(MaintenanceWindowAccessDeniedException.class, () -> service.update(OTHER_ORG_ID, 1L,
                UpdateMaintenanceWindowRequest.builder().name("x").build()));
        assertThrows(MaintenanceWindowAccessDeniedException.class,
                () -> service.delete(OTHER_ORG_ID, 1L));
    }

    @Test
    void updateMissingWindowReturnsNotFound() {
        when(windowRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(MaintenanceWindowNotFoundException.class, () -> service.update(ORG_ID, 99L,
                UpdateMaintenanceWindowRequest.builder().name("x").build()));
    }

    @Test
    void isMutedMatchesGlobalApplicationAndRuleScopes() {
        Instant now = Instant.parse("2026-10-21T11:00:00Z");

        when(windowRepository.findByOrganizationIdAndActiveTrueAndStartTimeLessThanEqualAndEndTimeAfter(
                eq(ORG_ID), eq(now), eq(now))).thenReturn(List.of(
                        window(MaintenanceScopeType.GLOBAL, null, true)));
        assertTrue(service.isMuted(ORG_ID, APP_ID, RULE_ID, now));

        when(windowRepository.findByOrganizationIdAndActiveTrueAndStartTimeLessThanEqualAndEndTimeAfter(
                eq(ORG_ID), eq(now), eq(now))).thenReturn(List.of(
                        window(MaintenanceScopeType.APPLICATION, APP_ID, true)));
        assertTrue(service.isMuted(ORG_ID, APP_ID, RULE_ID, now));
        assertFalse(service.isMuted(ORG_ID, 8L, RULE_ID, now));
        assertFalse(service.isMuted(ORG_ID, null, RULE_ID, now));

        when(windowRepository.findByOrganizationIdAndActiveTrueAndStartTimeLessThanEqualAndEndTimeAfter(
                eq(ORG_ID), eq(now), eq(now))).thenReturn(List.of(
                        window(MaintenanceScopeType.SPECIFIC_RULE, RULE_ID, true)));
        assertTrue(service.isMuted(ORG_ID, APP_ID, RULE_ID, now));
        assertFalse(service.isMuted(ORG_ID, APP_ID, 12L, now));
    }

    @Test
    void isMutedIgnoresOtherTenants() {
        Instant now = Instant.parse("2026-10-21T11:00:00Z");
        when(windowRepository.findByOrganizationIdAndActiveTrueAndStartTimeLessThanEqualAndEndTimeAfter(
                anyLong(), any(), any())).thenReturn(List.of());

        assertFalse(service.isMuted(OTHER_ORG_ID, APP_ID, RULE_ID, now));
        verify(windowRepository).findByOrganizationIdAndActiveTrueAndStartTimeLessThanEqualAndEndTimeAfter(
                OTHER_ORG_ID, now, now);
    }
}
