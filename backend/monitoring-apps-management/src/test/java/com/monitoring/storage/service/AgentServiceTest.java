package com.monitoring.storage.service;

import com.monitoring.storage.dto.AgentNodeResponse;
import com.monitoring.storage.entity.AgentNode;
import com.monitoring.storage.entity.Application;
import com.monitoring.storage.exception.ApplicationAccessDeniedException;
import com.monitoring.storage.repository.AgentNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Instant SEEN_AT = Instant.parse("2026-10-18T12:00:00Z");

    private AgentNodeRepository agentNodeRepository;
    private ApplicationService applicationService;
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        agentNodeRepository = Mockito.mock(AgentNodeRepository.class);
        applicationService = Mockito.mock(ApplicationService.class);
        agentService = new AgentService(agentNodeRepository, applicationService);
    }

    private Application application() {
        return Application.builder()
                .id(10L)
                .name("my-app")
                .apiKeyHash("hashed-key")
                .organizationId(OWNER_ID)
                .build();
    }

    @Test
    void recordHeartbeat_newHost_insertsNodeWithVersion() {
        Application application = application();
        when(agentNodeRepository.findByApplicationIdAndHostname(10L, "server-1")).thenReturn(Optional.empty());
        when(agentNodeRepository.save(any(AgentNode.class))).thenAnswer(inv -> inv.getArgument(0));

        agentService.recordHeartbeat(application, "server-1", "0.1.0", SEEN_AT);

        ArgumentCaptor<AgentNode> captor = ArgumentCaptor.forClass(AgentNode.class);
        verify(agentNodeRepository).save(captor.capture());
        AgentNode saved = captor.getValue();
        assertEquals(application, saved.getApplication());
        assertEquals("server-1", saved.getHostname());
        assertEquals("0.1.0", saved.getAgentVersion());
        assertEquals(SEEN_AT, saved.getLastSeenAt());
    }

    @Test
    void recordHeartbeat_existingHost_updatesLastSeenAndVersion() {
        AgentNode existing = AgentNode.builder()
                .id(5L)
                .application(application())
                .hostname("server-1")
                .agentVersion("0.0.9")
                .lastSeenAt(Instant.parse("2026-10-18T10:00:00Z"))
                .build();
        when(agentNodeRepository.findByApplicationIdAndHostname(10L, "server-1")).thenReturn(Optional.of(existing));
        when(agentNodeRepository.save(any(AgentNode.class))).thenAnswer(inv -> inv.getArgument(0));

        agentService.recordHeartbeat(application(), "server-1", "0.1.0", SEEN_AT);

        assertEquals(SEEN_AT, existing.getLastSeenAt());
        assertEquals("0.1.0", existing.getAgentVersion());
        verify(agentNodeRepository).save(existing);
    }

    @Test
    void recordHeartbeat_missingVersion_keepsPreviouslyReportedVersion() {
        AgentNode existing = AgentNode.builder()
                .id(5L)
                .application(application())
                .hostname("server-1")
                .agentVersion("0.1.0")
                .lastSeenAt(Instant.parse("2026-10-18T10:00:00Z"))
                .build();
        when(agentNodeRepository.findByApplicationIdAndHostname(10L, "server-1")).thenReturn(Optional.of(existing));
        when(agentNodeRepository.save(any(AgentNode.class))).thenAnswer(inv -> inv.getArgument(0));

        agentService.recordHeartbeat(application(), "server-1", null, SEEN_AT);

        assertEquals("0.1.0", existing.getAgentVersion());
        assertEquals(SEEN_AT, existing.getLastSeenAt());
    }

    @Test
    void recordHeartbeat_blankHost_isIgnored() {
        agentService.recordHeartbeat(application(), "  ", "0.1.0", SEEN_AT);

        verify(agentNodeRepository, never()).save(any());
    }

    @Test
    void listForApplication_wrongOwner_throwsAccessDenied() {
        Mockito.doThrow(new ApplicationAccessDeniedException())
                .when(applicationService).getOwnedApplication(10L, OTHER_USER_ID);

        assertThrows(ApplicationAccessDeniedException.class,
                () -> agentService.listForApplication(10L, OTHER_USER_ID));
        verify(agentNodeRepository, never()).findByApplicationIdOrderByHostnameAsc(any());
    }

    @Test
    void listForApplication_owner_returnsMappedNodes() {
        AgentNode node = AgentNode.builder()
                .id(5L)
                .application(application())
                .hostname("server-1")
                .agentVersion("0.1.0")
                .lastSeenAt(SEEN_AT)
                .build();
        when(agentNodeRepository.findByApplicationIdOrderByHostnameAsc(10L)).thenReturn(List.of(node));

        List<AgentNodeResponse> result = agentService.listForApplication(10L, OWNER_ID);

        assertEquals(1, result.size());
        assertEquals("server-1", result.get(0).hostname());
        assertEquals("0.1.0", result.get(0).agentVersion());
        assertEquals(SEEN_AT, result.get(0).lastSeenAt());
    }
}
