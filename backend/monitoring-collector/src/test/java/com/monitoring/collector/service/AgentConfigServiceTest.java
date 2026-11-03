package com.monitoring.collector.service;

import com.monitoring.apps.entity.Application;
import com.monitoring.apps.repository.AgentNodeRepository;
import com.monitoring.apps.service.AgentService;
import com.monitoring.apps.service.ApplicationService;
import com.monitoring.collector.dto.request.AgentConfigStatusRequest;
import com.monitoring.collector.dto.request.CreateAgentConfigTemplateRequest;
import com.monitoring.collector.dto.response.ActiveAgentConfigResponse;
import com.monitoring.collector.entity.AgentConfigAssignment;
import com.monitoring.collector.entity.AgentConfigStatus;
import com.monitoring.collector.entity.AgentConfigTemplate;
import com.monitoring.collector.exception.AgentConfigTemplateNameConflictException;
import com.monitoring.collector.repository.AgentConfigAssignmentRepository;
import com.monitoring.collector.repository.AgentConfigTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentConfigServiceTest {

    private static final Long ORG_ID = 3L;
    private static final Long APP_ID = 7L;

    private AgentConfigTemplateRepository templateRepository;
    private AgentConfigAssignmentRepository assignmentRepository;
    private AgentNodeRepository agentNodeRepository;
    private ApplicationService applicationService;
    private AgentService agentService;
    private AgentConfigService service;

    @BeforeEach
    void setUp() {
        templateRepository = Mockito.mock(AgentConfigTemplateRepository.class);
        assignmentRepository = Mockito.mock(AgentConfigAssignmentRepository.class);
        agentNodeRepository = Mockito.mock(AgentNodeRepository.class);
        applicationService = Mockito.mock(ApplicationService.class);
        agentService = Mockito.mock(AgentService.class);
        service = new AgentConfigService(
                templateRepository,
                assignmentRepository,
                agentNodeRepository,
                applicationService,
                agentService);
    }

    private Application application() {
        return Application.builder().id(APP_ID).organizationId(ORG_ID).name("api").build();
    }

    @Test
    void createDefaultClearsOtherDefaultsAndMarksAgentsPending() {
        CreateAgentConfigTemplateRequest request = new CreateAgentConfigTemplateRequest();
        request.setName(" Production ");
        request.setConfigYaml("interval_seconds: 30\n");
        request.setIsDefault(true);
        when(templateRepository.countByOrganizationId(ORG_ID)).thenReturn(1L);
        when(templateRepository.existsByOrganizationIdAndName(ORG_ID, "Production")).thenReturn(false);
        when(templateRepository.save(any(AgentConfigTemplate.class))).thenAnswer(invocation -> {
            AgentConfigTemplate template = invocation.getArgument(0);
            template.setId(11L);
            return template;
        });

        var response = service.create(ORG_ID, request);

        assertEquals(11L, response.id());
        assertTrue(response.isDefault());
        verify(templateRepository).clearDefault(ORG_ID, 11L);
        verify(agentNodeRepository).markConfigPendingByOrganizationId(ORG_ID);
    }

    @Test
    void createRejectsDuplicateNameWithinTenant() {
        CreateAgentConfigTemplateRequest request = new CreateAgentConfigTemplateRequest();
        request.setName("prod");
        request.setConfigYaml("{}\n");
        when(templateRepository.existsByOrganizationIdAndName(ORG_ID, "prod")).thenReturn(true);

        assertThrows(AgentConfigTemplateNameConflictException.class,
                () -> service.create(ORG_ID, request));
        verify(templateRepository, never()).save(any());
    }

    @Test
    void activeConfigMergesDefaultAndAssignedTemplate() {
        Application application = application();
        AgentConfigTemplate defaults = AgentConfigTemplate.builder()
                .id(1L)
                .organizationId(ORG_ID)
                .name("default")
                .configYaml("interval_seconds: 15\n")
                .isDefault(true)
                .build();
        AgentConfigTemplate assigned = AgentConfigTemplate.builder()
                .id(2L)
                .organizationId(ORG_ID)
                .name("api")
                .configYaml("interval_seconds: 30\n")
                .build();
        when(templateRepository.findFirstByOrganizationIdAndIsDefaultTrue(ORG_ID))
                .thenReturn(Optional.of(defaults));
        when(assignmentRepository.findByApplicationId(APP_ID)).thenReturn(Optional.of(
                AgentConfigAssignment.builder().application(application).template(assigned).build()));

        ActiveAgentConfigResponse response = service.activeConfig(application);

        assertTrue(response.configYaml().contains("interval_seconds: 30"));
        assertEquals(64, response.configVersion().length());
    }

    @Test
    void staleSyncedReportIsDowngradedToPending() {
        Application application = application();
        AgentConfigTemplate defaults = AgentConfigTemplate.builder()
                .id(1L)
                .organizationId(ORG_ID)
                .configYaml("interval_seconds: 15\n")
                .isDefault(true)
                .build();
        when(templateRepository.findFirstByOrganizationIdAndIsDefaultTrue(ORG_ID))
                .thenReturn(Optional.of(defaults));
        when(assignmentRepository.findByApplicationId(APP_ID)).thenReturn(Optional.empty());

        AgentConfigStatusRequest request = new AgentConfigStatusRequest();
        request.setHostname("server-1");
        request.setConfigVersion("old-version");
        request.setStatus(AgentConfigStatus.SYNCED);

        service.reportStatus(application, request);

        verify(agentService).reportConfigStatus(
                eq(application), eq("server-1"), eq("old-version"), eq("PENDING"), isNull(), any());
    }

    @Test
    void errorReportIsPassedThroughWithItsMessage() {
        Application application = application();
        when(templateRepository.findFirstByOrganizationIdAndIsDefaultTrue(ORG_ID))
                .thenReturn(Optional.empty());
        when(assignmentRepository.findByApplicationId(APP_ID)).thenReturn(Optional.empty());

        AgentConfigStatusRequest request = new AgentConfigStatusRequest();
        request.setHostname("server-1");
        request.setConfigVersion("bad-version");
        request.setStatus(AgentConfigStatus.ERROR);
        request.setErrorMessage("invalid interval");

        service.reportStatus(application, request);

        verify(agentService).reportConfigStatus(
                eq(application), eq("server-1"), eq("bad-version"), eq("ERROR"),
                eq("invalid interval"), any());
    }

    @Test
    void assignChecksApplicationOwnershipAndMarksThatApplicationPending() {
        Application application = application();
        AgentConfigTemplate template = AgentConfigTemplate.builder()
                .id(5L)
                .organizationId(ORG_ID)
                .name("api")
                .configYaml("{}\n")
                .build();
        AgentConfigAssignment assignment = AgentConfigAssignment.builder()
                .application(application)
                .template(template)
                .build();
        when(applicationService.getOwnedApplication(APP_ID, ORG_ID)).thenReturn(application);
        when(templateRepository.findByIdAndOrganizationId(5L, ORG_ID)).thenReturn(Optional.of(template));
        when(templateRepository.findFirstByOrganizationIdAndIsDefaultTrue(ORG_ID))
                .thenReturn(Optional.empty());
        when(assignmentRepository.findByApplicationId(APP_ID))
                .thenReturn(Optional.empty(), Optional.of(assignment));

        var request = new com.monitoring.collector.dto.request.AssignAgentConfigTemplateRequest();
        request.setTemplateId(5L);

        var response = service.assign(ORG_ID, APP_ID, request);

        assertEquals(5L, response.assignedTemplateId());
        verify(applicationService).getOwnedApplication(APP_ID, ORG_ID);
        verify(agentNodeRepository).markConfigPendingByApplicationId(APP_ID);
    }

    @Test
    void listIsScopedToTheTenant() {
        when(templateRepository.findByOrganizationIdOrderByNameAsc(ORG_ID)).thenReturn(List.of());

        assertTrue(service.list(ORG_ID).isEmpty());
        verify(templateRepository).findByOrganizationIdOrderByNameAsc(ORG_ID);
    }
}
