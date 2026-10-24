package com.monitoring.alerts.service;

import com.monitoring.alerts.dto.request.CreateNotificationChannelRequest;
import com.monitoring.alerts.dto.response.NotificationChannelResponse;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.exception.InvalidNotificationChannelException;
import com.monitoring.alerts.exception.NotificationChannelLimitExceededException;
import com.monitoring.alerts.repository.AlertIncidentRepository;
import com.monitoring.alerts.repository.AlertRuleRepository;
import com.monitoring.alerts.repository.NotificationChannelRepository;
import com.monitoring.apps.service.ApplicationService;
import com.monitoring.auth.repository.UserRepository;
import com.monitoring.collector.service.LogQueryService;
import com.monitoring.healthcheck.service.HealthCheckService;
import com.monitoring.healthcheck.service.SsrfGuard;
import com.monitoring.revisions.service.ConfigRevisionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for type-specific notification channel validation. The SSRF guard is mocked so the
 * tests do not depend on DNS resolution.
 */
class NotificationChannelValidationTest {

    private static final Long OWNER_ID = 1L;

    private NotificationChannelRepository channelRepository;
    private AlertService service;

    @BeforeEach
    void setUp() throws Exception {
        AlertRuleRepository ruleRepository = Mockito.mock(AlertRuleRepository.class);
        channelRepository = Mockito.mock(NotificationChannelRepository.class);
        AlertIncidentRepository incidentRepository = Mockito.mock(AlertIncidentRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        ApplicationService applicationService = Mockito.mock(ApplicationService.class);
        HealthCheckService healthCheckService = Mockito.mock(HealthCheckService.class);
        AlertNotificationDispatcher dispatcher = Mockito.mock(AlertNotificationDispatcher.class);
        LogQueryService logQueryService = Mockito.mock(LogQueryService.class);
        AlertPreviewRateLimiter previewRateLimiter = Mockito.mock(AlertPreviewRateLimiter.class);

        SsrfGuard ssrfGuard = Mockito.mock(SsrfGuard.class);
        when(ssrfGuard.parse(anyString())).thenAnswer(invocation -> new URI(invocation.getArgument(0)));

        service = new AlertService(ruleRepository, channelRepository, incidentRepository, userRepository,
                applicationService, healthCheckService, ssrfGuard,
                new NotificationUrlPolicy(ssrfGuard, false), dispatcher,
                logQueryService, previewRateLimiter, Mockito.mock(ConfigRevisionService.class),
                new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator());
        when(channelRepository.save(any(NotificationChannel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createSlackChannelAcceptsSlackHost() {
        NotificationChannelResponse response = service.createChannel(OWNER_ID,
                CreateNotificationChannelRequest.builder()
                        .type(NotificationChannelType.SLACK)
                        .slackWebhookUrl("https://hooks.slack.com/services/T000/B000/XXXX")
                        .build());

        assertEquals(NotificationChannelType.SLACK, response.getType());
        assertEquals("https://hooks.slack.com/****XXXX", response.getSlackWebhookUrl());
        assertNull(response.getUrl());
        assertNull(response.getEmailAddress());
    }

    @Test
    void createSlackChannelRejectsNonSlackHost() {
        assertThrows(InvalidNotificationChannelException.class, () -> service.createChannel(OWNER_ID,
                CreateNotificationChannelRequest.builder()
                        .type(NotificationChannelType.SLACK)
                        .slackWebhookUrl("https://evil.example.com/hook")
                        .build()));
        verify(channelRepository, never()).save(any());
    }

    @Test
    void createSlackChannelRequiresUrl() {
        assertThrows(InvalidNotificationChannelException.class, () -> service.createChannel(OWNER_ID,
                CreateNotificationChannelRequest.builder()
                        .type(NotificationChannelType.SLACK)
                        .build()));
    }

    @Test
    void createEmailChannelAcceptsValidAddress() {
        NotificationChannelResponse response = service.createChannel(OWNER_ID,
                CreateNotificationChannelRequest.builder()
                        .type(NotificationChannelType.EMAIL)
                        .emailAddress("oncall@example.com")
                        .build());

        assertEquals(NotificationChannelType.EMAIL, response.getType());
        assertEquals("oncall@example.com", response.getEmailAddress());
        assertNull(response.getUrl());
    }

    @Test
    void createEmailChannelRejectsInvalidAddress() {
        assertThrows(InvalidNotificationChannelException.class, () -> service.createChannel(OWNER_ID,
                CreateNotificationChannelRequest.builder()
                        .type(NotificationChannelType.EMAIL)
                        .emailAddress("not-an-email")
                        .build()));
        verify(channelRepository, never()).save(any());
    }

    @Test
    void createWebhookChannelRequiresUrl() {
        assertThrows(InvalidNotificationChannelException.class, () -> service.createChannel(OWNER_ID,
                CreateNotificationChannelRequest.builder()
                        .type(NotificationChannelType.WEBHOOK)
                        .build()));
    }

    @Test
    void webhookSecretAndHeaderValuesAreNeverReturned() {
        NotificationChannelResponse response = service.createChannel(OWNER_ID,
                CreateNotificationChannelRequest.builder()
                        .type(NotificationChannelType.WEBHOOK)
                        .url("https://8.8.8.8/hooks/secret-path")
                        .secret("super-secret-value")
                        .headers(Map.of("X-Custom", "header-secret"))
                        .build());

        assertTrue(response.isHasWebhookSecret());
        assertEquals(java.util.List.of("X-Custom"), response.getWebhookHeaderNames());
        assertFalse(response.getUrl().contains("secret-path"));
        assertFalse(response.getUrl().contains("header-secret"));
    }

    @Test
    void webhookRejectsDuplicateHeaderNamesIgnoringCase() {
        assertThrows(InvalidNotificationChannelException.class, () -> service.createChannel(OWNER_ID,
                CreateNotificationChannelRequest.builder()
                        .type(NotificationChannelType.WEBHOOK)
                        .url("https://8.8.8.8/hook")
                        .headers(Map.of("X-Custom", "one", "x-custom", "two"))
                        .build()));
        verify(channelRepository, never()).save(any());
    }

    @Test
    void pagerDutyChannelMasksRoutingKey() {
        NotificationChannelResponse response = service.createChannel(OWNER_ID,
                CreateNotificationChannelRequest.builder()
                        .type(NotificationChannelType.PAGERDUTY)
                        .routingKey("abcdefghijklmnop1234")
                        .build());

        assertEquals("****1234", response.getPagerDutyRoutingKey());
        assertNull(response.getUrl());
    }

    @Test
    void pagerDutyChannelRejectsMalformedRoutingKey() {
        assertThrows(InvalidNotificationChannelException.class, () -> service.createChannel(OWNER_ID,
                CreateNotificationChannelRequest.builder()
                        .type(NotificationChannelType.PAGERDUTY)
                        .routingKey("not valid!")
                        .build()));
    }

    @Test
    void teamsChannelMasksWebhookUrl() {
        NotificationChannelResponse response = service.createChannel(OWNER_ID,
                CreateNotificationChannelRequest.builder()
                        .type(NotificationChannelType.TEAMS)
                        .teamsWebhookUrl("https://8.8.8.8/webhook/teams-secret")
                        .build());

        assertEquals("https://8.8.8.8/****cret", response.getTeamsWebhookUrl());
    }

    @Test
    void channelCreationIsRejectedAtTheTenantLimit() {
        when(channelRepository.countByOrganizationId(OWNER_ID))
                .thenReturn((long) AlertService.MAX_NOTIFICATION_CHANNELS);

        assertThrows(NotificationChannelLimitExceededException.class, () -> service.createChannel(
                OWNER_ID,
                CreateNotificationChannelRequest.builder()
                        .type(NotificationChannelType.EMAIL)
                        .emailAddress("oncall@example.com")
                        .build()));
        verify(channelRepository, never()).save(any());
    }
}
