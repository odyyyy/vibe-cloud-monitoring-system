package com.monitoring.alerts.service;

import com.monitoring.alerts.dto.AlertIncidentResponse;
import com.monitoring.alerts.dto.AlertRuleResponse;
import com.monitoring.alerts.dto.CreateAlertRuleRequest;
import com.monitoring.alerts.dto.CreateNotificationChannelRequest;
import com.monitoring.alerts.dto.NotificationChannelResponse;
import com.monitoring.alerts.dto.UpdateAlertRuleRequest;
import com.monitoring.alerts.dto.UpdateNotificationChannelRequest;
import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertIncident;
import com.monitoring.alerts.entity.AlertRule;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.exception.AlertRuleAccessDeniedException;
import com.monitoring.alerts.exception.AlertRuleNotFoundException;
import com.monitoring.alerts.exception.InvalidAlertRuleException;
import com.monitoring.alerts.exception.InvalidWebhookUrlException;
import com.monitoring.alerts.exception.NotificationChannelAccessDeniedException;
import com.monitoring.alerts.exception.NotificationChannelNotFoundException;
import com.monitoring.alerts.repository.AlertIncidentRepository;
import com.monitoring.alerts.repository.AlertRuleRepository;
import com.monitoring.alerts.repository.NotificationChannelRepository;
import com.monitoring.healthcheck.exception.HealthCheckAccessDeniedException;
import com.monitoring.healthcheck.exception.HealthCheckNotFoundException;
import com.monitoring.healthcheck.service.HealthCheckService;
import com.monitoring.healthcheck.service.SsrfGuard;
import com.monitoring.storage.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Application service for alert rules, notification channels and incident history.
 *
 * <p>Every operation is scoped to the authenticated {@code ownerId}: a resource that exists but
 * belongs to another user raises an {@code *AccessDeniedException} (HTTP 403), while a missing
 * one raises a {@code *NotFoundException} (HTTP 404). Rules that reference an application or a
 * health check are validated against that resource's ownership before they are stored, so a
 * tenant cannot point a rule at another tenant's data.
 */
@Service
@RequiredArgsConstructor
public class AlertService {

    public static final int DEFAULT_INCIDENTS_LIMIT = 50;
    public static final int MAX_INCIDENTS_LIMIT = 500;

    private static final String WEBHOOK_SCHEME = "https";

    private final AlertRuleRepository ruleRepository;
    private final NotificationChannelRepository channelRepository;
    private final AlertIncidentRepository incidentRepository;
    private final ApplicationService applicationService;
    private final HealthCheckService healthCheckService;
    private final SsrfGuard ssrfGuard;

    // ---------------------------------------------------------------------------------------
    // Alert rules
    // ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AlertRuleResponse> listRules(Long ownerId) {
        return ruleRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(AlertRuleResponse::fromEntity)
                .toList();
    }

    @Transactional
    public AlertRuleResponse createRule(Long ownerId, CreateAlertRuleRequest request) {
        AlertRuleType type = request.getType();
        String target = request.getTarget().trim();
        Long applicationId = normalizeApplicationId(ownerId, type, request.getApplicationId(), target);

        AlertRule rule = AlertRule.builder()
                .ownerId(ownerId)
                .applicationId(applicationId)
                .name(request.getName().trim())
                .type(type)
                .target(target)
                .condition(request.getCondition())
                .threshold(request.getThreshold())
                .durationMinutes(request.getDurationMinutes())
                .active(request.getActive() == null || request.getActive())
                .build();

        return AlertRuleResponse.fromEntity(ruleRepository.save(rule));
    }

    @Transactional
    public AlertRuleResponse updateRule(Long ownerId, Long id, UpdateAlertRuleRequest request) {
        AlertRule rule = getOwnedRule(ownerId, id);

        if (request.getName() != null && !request.getName().isBlank()) {
            rule.setName(request.getName().trim());
        }
        if (request.getType() != null) {
            rule.setType(request.getType());
        }
        if (request.getTarget() != null && !request.getTarget().isBlank()) {
            rule.setTarget(request.getTarget().trim());
        }
        if (request.getCondition() != null) {
            rule.setCondition(request.getCondition());
        }
        if (request.getThreshold() != null) {
            rule.setThreshold(request.getThreshold());
        }
        if (request.getDurationMinutes() != null) {
            rule.setDurationMinutes(request.getDurationMinutes());
        }

        // Re-validate the merged rule: a partial update may have switched the type or target.
        Long requestedApplicationId = request.getApplicationId();
        Long normalizedApplicationId = normalizeApplicationId(
                ownerId, rule.getType(),
                requestedApplicationId != null ? requestedApplicationId : rule.getApplicationId(),
                rule.getTarget());
        rule.setApplicationId(normalizedApplicationId);

        if (request.getActive() != null) {
            rule.setActive(request.getActive());
            if (!request.getActive()) {
                // A deactivated rule must not keep an incident firing forever.
                rule.setBreachStartedAt(null);
                resolveOpenIncidents(rule.getId());
            }
        }

        return AlertRuleResponse.fromEntity(ruleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(Long ownerId, Long id) {
        AlertRule rule = getOwnedRule(ownerId, id);
        ruleRepository.delete(rule);
    }

    // ---------------------------------------------------------------------------------------
    // Notification channels
    // ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<NotificationChannelResponse> listChannels(Long ownerId) {
        return channelRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(NotificationChannelResponse::fromEntity)
                .toList();
    }

    @Transactional
    public NotificationChannelResponse createChannel(Long ownerId, CreateNotificationChannelRequest request) {
        NotificationChannel channel = NotificationChannel.builder()
                .ownerId(ownerId)
                .type(request.getType())
                .url(validateWebhookUrl(request.getUrl()))
                .active(request.getActive() == null || request.getActive())
                .build();

        return NotificationChannelResponse.fromEntity(channelRepository.save(channel));
    }

    @Transactional
    public NotificationChannelResponse updateChannel(Long ownerId, Long id, UpdateNotificationChannelRequest request) {
        NotificationChannel channel = getOwnedChannel(ownerId, id);

        if (request.getType() != null) {
            channel.setType(request.getType());
        }
        if (request.getUrl() != null && !request.getUrl().isBlank()) {
            channel.setUrl(validateWebhookUrl(request.getUrl()));
        }
        if (request.getActive() != null) {
            channel.setActive(request.getActive());
        }

        return NotificationChannelResponse.fromEntity(channelRepository.save(channel));
    }

    @Transactional
    public void deleteChannel(Long ownerId, Long id) {
        NotificationChannel channel = getOwnedChannel(ownerId, id);
        channelRepository.delete(channel);
    }

    // ---------------------------------------------------------------------------------------
    // Incidents
    // ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AlertIncidentResponse> listIncidents(Long ownerId, Integer limit) {
        int effectiveLimit = limit == null ? DEFAULT_INCIDENTS_LIMIT : limit;
        effectiveLimit = Math.max(1, Math.min(effectiveLimit, MAX_INCIDENTS_LIMIT));
        return incidentRepository
                .findByOwnerIdOrderByTriggeredAtDesc(ownerId, PageRequest.of(0, effectiveLimit)).stream()
                .map(AlertIncidentResponse::fromEntity)
                .toList();
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private AlertRule getOwnedRule(Long ownerId, Long id) {
        AlertRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new AlertRuleNotFoundException(id));
        if (!rule.getOwnerId().equals(ownerId)) {
            throw new AlertRuleAccessDeniedException();
        }
        return rule;
    }

    private NotificationChannel getOwnedChannel(Long ownerId, Long id) {
        NotificationChannel channel = channelRepository.findById(id)
                .orElseThrow(() -> new NotificationChannelNotFoundException(id));
        if (!channel.getOwnerId().equals(ownerId)) {
            throw new NotificationChannelAccessDeniedException();
        }
        return channel;
    }

    /**
     * Validates the type-specific target and returns the application id to persist (null for
     * health-check rules). Throws {@link InvalidAlertRuleException} for inconsistent payloads.
     */
    private Long normalizeApplicationId(Long ownerId, AlertRuleType type, Long applicationId, String target) {
        if (type == null) {
            throw new InvalidAlertRuleException("Alert rule type is required");
        }
        if (target == null || target.isBlank()) {
            throw new InvalidAlertRuleException("Alert rule target is required");
        }
        if (type == AlertRuleType.METRIC_THRESHOLD) {
            if (applicationId == null) {
                throw new InvalidAlertRuleException("A metric-threshold rule requires an application");
            }
            // Verifies ownership; throws ApplicationAccessDenied/NotFound on a foreign or missing app.
            applicationService.getOwnedApplication(applicationId, ownerId);
            return applicationId;
        }

        long healthCheckId;
        try {
            healthCheckId = Long.parseLong(target);
        } catch (NumberFormatException ex) {
            throw new InvalidAlertRuleException("A health-check rule target must be a health check id");
        }
        // Verifies ownership; maps a missing/foreign check to an invalid-rule error.
        try {
            healthCheckService.listResults(ownerId, healthCheckId, 1);
        } catch (HealthCheckNotFoundException | HealthCheckAccessDeniedException ex) {
            throw new InvalidAlertRuleException("The referenced health check does not exist or is not yours");
        }
        return null;
    }

    /**
     * Webhook URLs must be https and resolve exclusively to public addresses (SSRF protection),
     * so a tenant cannot make the platform POST to internal services.
     */
    private String validateWebhookUrl(String rawUrl) {
        URI uri = ssrfGuard.parse(rawUrl.trim());
        if (!WEBHOOK_SCHEME.equals(uri.getScheme().toLowerCase(Locale.ROOT))) {
            throw new InvalidWebhookUrlException("Webhook URL must use https");
        }
        try {
            ssrfGuard.assertPublicHost(uri);
        } catch (RuntimeException ex) {
            throw new InvalidWebhookUrlException(ex.getMessage());
        }
        return uri.toString();
    }

    private void resolveOpenIncidents(Long ruleId) {
        Instant now = Instant.now();
        for (AlertIncident incident : incidentRepository.findByRule_IdAndStatus(ruleId, IncidentStatus.FIRING)) {
            incident.setStatus(IncidentStatus.RESOLVED);
            incident.setResolvedAt(now);
            incidentRepository.save(incident);
        }
    }
}
