package com.monitoring.alerts.service;

import com.monitoring.alerts.dto.request.AlertConditionRequest;
import com.monitoring.alerts.dto.request.CreateAlertRuleRequest;
import com.monitoring.alerts.dto.request.CreateNotificationChannelRequest;
import com.monitoring.alerts.dto.request.UpdateAlertRuleRequest;
import com.monitoring.alerts.dto.request.UpdateIncidentNotesRequest;
import com.monitoring.alerts.dto.request.UpdateNotificationChannelRequest;
import com.monitoring.alerts.dto.response.AlertIncidentResponse;
import com.monitoring.alerts.dto.response.AlertRuleResponse;
import com.monitoring.alerts.dto.response.NotificationChannelResponse;
import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertIncident;
import com.monitoring.alerts.entity.AlertRule;
import com.monitoring.alerts.entity.AlertRuleCondition;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.entity.LogicOperator;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.event.AlertEvent;
import com.monitoring.alerts.exception.AlertIncidentAccessDeniedException;
import com.monitoring.alerts.exception.AlertIncidentNotFoundException;
import com.monitoring.alerts.exception.AlertRuleAccessDeniedException;
import com.monitoring.alerts.exception.AlertRuleNotFoundException;
import com.monitoring.alerts.exception.InvalidAlertRuleException;
import com.monitoring.alerts.exception.InvalidIncidentNoteException;
import com.monitoring.alerts.exception.InvalidIncidentStateException;
import com.monitoring.alerts.exception.InvalidNotificationChannelException;
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
import com.monitoring.apps.service.ApplicationService;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Application service for alert rules, notification channels and incident history.
 *
 * <p>Every operation is scoped to the authenticated {@code organizationId}: a resource that exists but
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
    private static final String SLACK_HOST_SUFFIX = "slack.com";
    /**
     * Deliberately conservative email check: a single {@code @}, a non-empty local part and a
     * dotted domain. Full RFC 5322 validation is unnecessary here and invitation to ReDoS; the
     * address is only a notification destination.
     */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    private final AlertRuleRepository ruleRepository;
    private final NotificationChannelRepository channelRepository;
    private final AlertIncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final ApplicationService applicationService;
    private final HealthCheckService healthCheckService;
    private final SsrfGuard ssrfGuard;
    private final AlertNotificationDispatcher notificationDispatcher;

    // ---------------------------------------------------------------------------------------
    // Alert rules
    // ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AlertRuleResponse> listRules(Long organizationId) {
        return listRules(organizationId, null, null);
    }

    /**
     * Lists rules scoped to the authenticated tenant. When {@code applicationId} is supplied only
     * active metric rules for that application are returned, and an optional {@code metricName}
     * narrows the result to rules that reference the metric in any condition (or in the legacy
     * target column). This backs the chart threshold overlay without exposing another tenant's
     * rules: the organization is always part of the query.
     */
    @Transactional(readOnly = true)
    public List<AlertRuleResponse> listRules(Long organizationId, Long applicationId, String metricName) {
        List<AlertRule> rules = applicationId == null
                ? ruleRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                : ruleRepository.findByOrganizationIdAndApplicationIdAndActiveTrueOrderByCreatedAtDesc(
                        organizationId, applicationId);

        String metricFilter = metricName == null || metricName.isBlank() ? null : metricName.trim();
        return rules.stream()
                .filter(rule -> metricFilter == null || referencesMetric(rule, metricFilter))
                .map(AlertRuleResponse::fromEntity)
                .toList();
    }

    /** True when the rule compares against {@code metricName} in any condition or legacy column. */
    private boolean referencesMetric(AlertRule rule, String metricName) {
        if (metricName.equals(rule.getTarget())) {
            return true;
        }
        return rule.getConditions().stream()
                .anyMatch(condition -> metricName.equals(condition.getTarget()));
    }

    @Transactional
    public AlertRuleResponse createRule(Long organizationId, CreateAlertRuleRequest request) {
        AlertRuleType type = request.getType();
        if (type == null) {
            throw new InvalidAlertRuleException("Alert rule type is required");
        }

        List<AlertConditionRequest> requested = request.getConditions() != null && !request.getConditions().isEmpty()
                ? request.getConditions()
                : List.of(new AlertConditionRequest(request.getTarget(), request.getCondition(), request.getThreshold()));

        List<AlertRuleCondition> conditions =
                materializeConditions(organizationId, type, request.getApplicationId(), requested);
        AlertRuleCondition primary = conditions.get(0);

        AlertRule rule = AlertRule.builder()
                .organizationId(organizationId)
                .applicationId(type == AlertRuleType.METRIC_THRESHOLD ? request.getApplicationId() : null)
                .name(request.getName().trim())
                .type(type)
                .target(primary.getTarget())
                .condition(primary.getOperator())
                .threshold(primary.getThreshold())
                .conditions(conditions)
                .logicOperator(request.getLogicOperator() == null ? LogicOperator.AND : request.getLogicOperator())
                .durationMinutes(request.getDurationMinutes())
                .active(request.getActive() == null || request.getActive())
                .build();

        return AlertRuleResponse.fromEntity(ruleRepository.save(rule));
    }

    @Transactional
    public AlertRuleResponse updateRule(Long organizationId, Long id, UpdateAlertRuleRequest request) {
        AlertRule rule = getOwnedRule(organizationId, id);

        if (request.getName() != null && !request.getName().isBlank()) {
            rule.setName(request.getName().trim());
        }
        if (request.getType() != null) {
            rule.setType(request.getType());
        }
        if (request.getDurationMinutes() != null) {
            rule.setDurationMinutes(request.getDurationMinutes());
        }
        if (request.getLogicOperator() != null) {
            rule.setLogicOperator(request.getLogicOperator());
        }

        Long applicationId = request.getApplicationId() != null ? request.getApplicationId() : rule.getApplicationId();

        List<AlertConditionRequest> requested = mergeConditionRequests(rule, request);
        List<AlertRuleCondition> conditions = materializeConditions(organizationId, rule.getType(), applicationId, requested);
        AlertRuleCondition primary = conditions.get(0);
        rule.setTarget(primary.getTarget());
        rule.setCondition(primary.getOperator());
        rule.setThreshold(primary.getThreshold());
        rule.setConditions(conditions);
        rule.setApplicationId(rule.getType() == AlertRuleType.METRIC_THRESHOLD ? applicationId : null);

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
    public void deleteRule(Long organizationId, Long id) {
        AlertRule rule = getOwnedRule(organizationId, id);
        ruleRepository.delete(rule);
    }

    // ---------------------------------------------------------------------------------------
    // Notification channels
    // ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<NotificationChannelResponse> listChannels(Long organizationId) {
        return channelRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(NotificationChannelResponse::fromEntity)
                .toList();
    }

    @Transactional
    public NotificationChannelResponse createChannel(Long organizationId, CreateNotificationChannelRequest request) {
        NotificationChannelType type = request.getType();
        if (type == null) {
            throw new InvalidNotificationChannelException("Notification channel type is required");
        }

        NotificationChannel channel = NotificationChannel.builder()
                .organizationId(organizationId)
                .type(type)
                .active(request.getActive() == null || request.getActive())
                .build();
        applyDestination(channel, request.getUrl(), request.getSlackWebhookUrl(), request.getEmailAddress());
        requireDestination(channel);

        return NotificationChannelResponse.fromEntity(channelRepository.save(channel));
    }

    @Transactional
    public NotificationChannelResponse updateChannel(Long organizationId, Long id, UpdateNotificationChannelRequest request) {
        NotificationChannel channel = getOwnedChannel(organizationId, id);

        if (request.getType() != null && request.getType() != channel.getType()) {
            // Switching type invalidates the previous destination; a new one must be supplied.
            channel.setType(request.getType());
            channel.setUrl(null);
            channel.setSlackWebhookUrl(null);
            channel.setEmailAddress(null);
        }
        applyDestination(channel, request.getUrl(), request.getSlackWebhookUrl(), request.getEmailAddress());
        if (request.getActive() != null) {
            channel.setActive(request.getActive());
        }
        requireDestination(channel);

        return NotificationChannelResponse.fromEntity(channelRepository.save(channel));
    }

    @Transactional
    public void deleteChannel(Long organizationId, Long id) {
        NotificationChannel channel = getOwnedChannel(organizationId, id);
        channelRepository.delete(channel);
    }

    /**
     * Sends a synthetic test notification to one owned channel so a user can verify the
     * configuration. The rate limiter is bypassed; delivery still happens asynchronously on the
     * notification executor and its failures are only logged.
     */
    @Transactional(readOnly = true)
    public void sendTestNotification(Long organizationId, Long id) {
        NotificationChannel channel = getOwnedChannel(organizationId, id);

        AlertEvent event = new AlertEvent(
                null,
                0L,
                "Test notification",
                organizationId,
                null,
                AlertRuleType.METRIC_THRESHOLD,
                "test.metric",
                AlertCondition.GREATER_THAN,
                0.0,
                1.0,
                List.of(new AlertEvent.ConditionObservation(
                        "test.metric", AlertCondition.GREATER_THAN, 0.0, 1.0, true)),
                LogicOperator.AND,
                IncidentStatus.FIRING,
                Instant.now());

        notificationDispatcher.dispatchTest(channel, event);
    }

    // ---------------------------------------------------------------------------------------
    // Incidents
    // ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AlertIncidentResponse> listIncidents(Long organizationId, Integer limit) {
        return listIncidents(organizationId, limit, null);
    }

    /** Incident history, optionally restricted to one lifecycle status. */
    @Transactional(readOnly = true)
    public List<AlertIncidentResponse> listIncidents(
            Long organizationId, Integer limit, IncidentStatus status) {
        int effectiveLimit = limit == null ? DEFAULT_INCIDENTS_LIMIT : limit;
        effectiveLimit = Math.max(1, Math.min(effectiveLimit, MAX_INCIDENTS_LIMIT));
        PageRequest page = PageRequest.of(0, effectiveLimit);
        List<AlertIncident> incidents = (status == null
                ? incidentRepository.findByOrganizationIdOrderByTriggeredAtDesc(organizationId, page)
                : incidentRepository.findByOrganizationIdAndStatusOrderByTriggeredAtDesc(
                        organizationId, status, page))
                .getContent();
        Map<Long, String> userNames = userNames(incidents);
        return incidents.stream()
                .map(incident -> AlertIncidentResponse.fromEntity(incident, userNames))
                .toList();
    }

    /**
     * Moves a firing incident to {@link IncidentStatus#ACKNOWLEDGED} and records who did it.
     * A resolved or already acknowledged incident is rejected with a conflict.
     */
    @Transactional
    public AlertIncidentResponse acknowledgeIncident(Long organizationId, Long userId, Long incidentId) {
        AlertIncident incident = getOwnedIncident(organizationId, incidentId);
        if (incident.getStatus() == IncidentStatus.RESOLVED) {
            throw new InvalidIncidentStateException("A resolved incident cannot be acknowledged");
        }
        if (incident.getStatus() == IncidentStatus.ACKNOWLEDGED) {
            throw new InvalidIncidentStateException("The incident is already acknowledged");
        }

        incident.setStatus(IncidentStatus.ACKNOWLEDGED);
        incident.setAcknowledgedBy(userId);
        incident.setAcknowledgedAt(Instant.now());
        return toResponse(incidentRepository.save(incident));
    }

    /**
     * Manually closes an incident. Both firing and acknowledged incidents may be resolved, but
     * an already resolved incident is never modified again.
     */
    @Transactional
    public AlertIncidentResponse resolveIncident(Long organizationId, Long userId, Long incidentId) {
        AlertIncident incident = getOwnedIncident(organizationId, incidentId);
        if (incident.getStatus() == IncidentStatus.RESOLVED) {
            throw new InvalidIncidentStateException("The incident is already resolved");
        }

        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedBy(userId);
        incident.setResolvedAt(Instant.now());
        return toResponse(incidentRepository.save(incident));
    }

    /** Replaces the plain-text notes. An empty value clears them. */
    @Transactional
    public AlertIncidentResponse updateIncidentNotes(
            Long organizationId, Long incidentId, UpdateIncidentNotesRequest request) {
        AlertIncident incident = getOwnedIncident(organizationId, incidentId);
        incident.setNotes(sanitizeNotes(request == null ? null : request.getNotes()));
        return toResponse(incidentRepository.save(incident));
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private AlertIncident getOwnedIncident(Long organizationId, Long incidentId) {
        AlertIncident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new AlertIncidentNotFoundException(incidentId));
        if (!incident.getOrganizationId().equals(organizationId)) {
            throw new AlertIncidentAccessDeniedException();
        }
        return incident;
    }

    /**
     * Rejects markup instead of storing it. The frontend renders notes as text, so escaping is
     * also in place, but keeping HTML out of the database removes the risk at the source.
     */
    private String sanitizeNotes(String rawNotes) {
        if (rawNotes == null) {
            throw new InvalidIncidentNoteException("Notes are required");
        }
        String notes = rawNotes.trim();
        if (notes.length() > AlertIncident.MAX_NOTES_LENGTH) {
            throw new InvalidIncidentNoteException(
                    "Notes must be at most " + AlertIncident.MAX_NOTES_LENGTH + " characters");
        }
        if (notes.indexOf('<') >= 0 || notes.indexOf('>') >= 0) {
            throw new InvalidIncidentNoteException("Notes must be plain text without HTML");
        }
        return notes.isEmpty() ? null : notes;
    }

    private AlertIncidentResponse toResponse(AlertIncident incident) {
        return AlertIncidentResponse.fromEntity(incident, userNames(List.of(incident)));
    }

    private Map<Long, String> userNames(Collection<AlertIncident> incidents) {
        List<Long> userIds = incidents.stream()
                .flatMap(incident -> java.util.stream.Stream.of(
                        incident.getAcknowledgedBy(), incident.getResolvedBy()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> names = new HashMap<>();
        for (User user : userRepository.findAllById(userIds)) {
            names.put(user.getId(), user.getUsername());
        }
        return names;
    }

    private AlertRule getOwnedRule(Long organizationId, Long id) {
        AlertRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new AlertRuleNotFoundException(id));
        if (!rule.getOrganizationId().equals(organizationId)) {
            throw new AlertRuleAccessDeniedException();
        }
        return rule;
    }

    private NotificationChannel getOwnedChannel(Long organizationId, Long id) {
        NotificationChannel channel = channelRepository.findById(id)
                .orElseThrow(() -> new NotificationChannelNotFoundException(id));
        if (!channel.getOrganizationId().equals(organizationId)) {
            throw new NotificationChannelAccessDeniedException();
        }
        return channel;
    }

    /**
     * Determines the effective condition list for an update. An explicit {@code conditions} list
     * replaces the whole list; legacy {@code target}/{@code condition}/{@code threshold} fields
     * update (or create) the primary condition while keeping any remaining compound conditions.
     */
    private List<AlertConditionRequest> mergeConditionRequests(AlertRule rule, UpdateAlertRuleRequest request) {
        if (request.getConditions() != null) {
            if (request.getConditions().isEmpty()) {
                throw new InvalidAlertRuleException("An alert rule requires at least one condition");
            }
            return request.getConditions();
        }

        boolean legacyProvided = request.getTarget() != null
                || request.getCondition() != null
                || request.getThreshold() != null;
        if (!legacyProvided) {
            return existingConditions(rule);
        }

        AlertConditionRequest primary = new AlertConditionRequest(
                request.getTarget() != null && !request.getTarget().isBlank()
                        ? request.getTarget().trim()
                        : rule.getTarget(),
                request.getCondition() != null ? request.getCondition() : rule.getCondition(),
                request.getThreshold() != null ? request.getThreshold() : rule.getThreshold());

        if (rule.getConditions().isEmpty()) {
            return List.of(primary);
        }
        List<AlertConditionRequest> merged = new ArrayList<>(existingConditions(rule));
        merged.set(0, primary);
        return merged;
    }

    private List<AlertConditionRequest> existingConditions(AlertRule rule) {
        if (!rule.getConditions().isEmpty()) {
            return rule.getConditions().stream()
                    .map(condition -> new AlertConditionRequest(
                            condition.getTarget(), condition.getOperator(), condition.getThreshold()))
                    .toList();
        }
        return List.of(new AlertConditionRequest(rule.getTarget(), rule.getCondition(), rule.getThreshold()));
    }

    /**
     * Validates the requested conditions and verifies that every target belongs to the tenant,
     * then returns the persistable condition list.
     */
    private List<AlertRuleCondition> materializeConditions(
            Long organizationId, AlertRuleType type, Long applicationId, List<AlertConditionRequest> requested) {
        if (type == null) {
            throw new InvalidAlertRuleException("Alert rule type is required");
        }
        if (requested.isEmpty()) {
            throw new InvalidAlertRuleException("An alert rule requires at least one condition");
        }
        if (requested.size() > AlertRule.MAX_CONDITIONS) {
            throw new InvalidAlertRuleException(
                    "At most " + AlertRule.MAX_CONDITIONS + " conditions are allowed");
        }

        if (type == AlertRuleType.METRIC_THRESHOLD) {
            if (applicationId == null) {
                throw new InvalidAlertRuleException("A metric-threshold rule requires an application");
            }
            // Verifies ownership; throws ApplicationAccessDenied/NotFound on a foreign or missing app.
            applicationService.getOwnedApplication(applicationId, organizationId);
        }

        List<AlertRuleCondition> conditions = new ArrayList<>(requested.size());
        for (AlertConditionRequest condition : requested) {
            if (condition == null || condition.getTarget() == null || condition.getTarget().isBlank()) {
                throw new InvalidAlertRuleException(
                        "Each condition requires a target, operator and threshold");
            }
            String target = condition.getTarget().trim();
            AlertCondition operator = condition.getOperator();
            Double threshold = condition.getThreshold();
            if (type == AlertRuleType.HEALTH_CHECK) {
                // Health-check conditions only need a target: the up/down state is the signal.
                validateHealthCheckTarget(organizationId, target);
                operator = operator == null ? AlertCondition.EQUAL : operator;
                threshold = threshold == null ? 0.0 : threshold;
            } else if (operator == null || threshold == null) {
                throw new InvalidAlertRuleException(
                        "Each condition requires a target, operator and threshold");
            }
            conditions.add(AlertRuleCondition.builder()
                    .target(target)
                    .operator(operator)
                    .threshold(threshold)
                    .build());
        }
        return conditions;
    }

    private void validateHealthCheckTarget(Long organizationId, String target) {
        long healthCheckId;
        try {
            healthCheckId = Long.parseLong(target);
        } catch (NumberFormatException ex) {
            throw new InvalidAlertRuleException("A health-check rule target must be a health check id");
        }
        // Verifies ownership; maps a missing/foreign check to an invalid-rule error.
        try {
            healthCheckService.listResults(organizationId, healthCheckId, 1);
        } catch (HealthCheckNotFoundException | HealthCheckAccessDeniedException ex) {
            throw new InvalidAlertRuleException("The referenced health check does not exist or is not yours");
        }
    }

    /**
     * Validates and stores the destination that matches the channel's type. Only non-blank values
     * are applied, so a partial update without a new destination keeps the existing one.
     */
    private void applyDestination(NotificationChannel channel, String rawUrl, String rawSlackUrl, String rawEmail) {
        if (rawUrl != null && !rawUrl.isBlank()) {
            channel.setUrl(validateWebhookUrl(rawUrl));
        }
        if (rawSlackUrl != null && !rawSlackUrl.isBlank()) {
            channel.setSlackWebhookUrl(validateSlackUrl(rawSlackUrl));
        }
        if (rawEmail != null && !rawEmail.isBlank()) {
            channel.setEmailAddress(validateEmail(rawEmail));
        }
    }

    /**
     * Ensures the channel has a destination for its type. WEBHOOK and SLACK store a URL,
     * EMAIL stores an address; the other columns are cleared so exactly one destination is kept.
     */
    private void requireDestination(NotificationChannel channel) {
        switch (channel.getType()) {
            case WEBHOOK -> {
                if (channel.getUrl() == null || channel.getUrl().isBlank()) {
                    throw new InvalidNotificationChannelException("A webhook channel requires a URL");
                }
                channel.setSlackWebhookUrl(null);
                channel.setEmailAddress(null);
            }
            case SLACK -> {
                if (channel.getSlackWebhookUrl() == null || channel.getSlackWebhookUrl().isBlank()) {
                    throw new InvalidNotificationChannelException("A Slack channel requires a webhook URL");
                }
                channel.setUrl(null);
                channel.setEmailAddress(null);
            }
            case EMAIL -> {
                if (channel.getEmailAddress() == null || channel.getEmailAddress().isBlank()) {
                    throw new InvalidNotificationChannelException("An email channel requires an address");
                }
                channel.setUrl(null);
                channel.setSlackWebhookUrl(null);
            }
        }
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

    /** Slack incoming webhooks must be https Slack hosts and resolve to public addresses. */
    private String validateSlackUrl(String rawUrl) {
        URI uri = ssrfGuard.parse(rawUrl.trim());
        if (!WEBHOOK_SCHEME.equals(uri.getScheme().toLowerCase(Locale.ROOT))) {
            throw new InvalidNotificationChannelException("Slack webhook URL must use https");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!host.equals(SLACK_HOST_SUFFIX) && !host.endsWith("." + SLACK_HOST_SUFFIX)) {
            throw new InvalidNotificationChannelException("Slack webhook URL must be a slack.com address");
        }
        try {
            ssrfGuard.assertPublicHost(uri);
        } catch (RuntimeException ex) {
            throw new InvalidNotificationChannelException(ex.getMessage());
        }
        return uri.toString();
    }

    private String validateEmail(String rawEmail) {
        String email = rawEmail.trim();
        if (email.length() > NotificationChannel.MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidNotificationChannelException("Enter a valid email address");
        }
        return email;
    }

    private void resolveOpenIncidents(Long ruleId) {
        Instant now = Instant.now();
        List<AlertIncident> open = incidentRepository.findByRule_IdAndStatusIn(
                ruleId, List.of(IncidentStatus.FIRING, IncidentStatus.ACKNOWLEDGED));
        for (AlertIncident incident : open) {
            incident.setStatus(IncidentStatus.RESOLVED);
            incident.setResolvedBy(null);
            incident.setResolvedAt(now);
            incidentRepository.save(incident);
        }
    }
}
