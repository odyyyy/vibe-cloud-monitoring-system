package com.monitoring.statuspage.service;

import com.monitoring.healthcheck.entity.HealthCheckResult;
import com.monitoring.healthcheck.entity.HealthStatus;
import com.monitoring.healthcheck.repository.HealthCheckResultRepository;
import com.monitoring.statuspage.dto.response.PublicStatusPageComponentResponse;
import com.monitoring.statuspage.dto.response.PublicStatusPageOutageResponse;
import com.monitoring.statuspage.dto.response.PublicStatusPageResponse;
import com.monitoring.statuspage.entity.ComponentStatus;
import com.monitoring.statuspage.entity.StatusPage;
import com.monitoring.statuspage.entity.StatusPageComponent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StatusPageStatusService {

    static final int STATUS_HISTORY_SIZE = 5;
    static final Duration OUTAGE_WINDOW = Duration.ofDays(14);
    static final int MAX_OUTAGES = 20;

    private final HealthCheckResultRepository resultRepository;

    public PublicStatusPageResponse toPublicResponse(StatusPage page, Instant now) {
        List<StatusPageComponent> components = page.getComponents().stream()
                .sorted(Comparator.comparingInt(StatusPageComponent::getSortOrder)
                        .thenComparing(StatusPageComponent::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        Map<Long, List<HealthCheckResult>> recentHistory = new HashMap<>();
        for (Long configId : linkedCheckIds(components)) {
            recentHistory.put(configId, resultRepository
                    .findByConfig_IdOrderByCheckedAtDescIdDesc(
                            configId, PageRequest.of(0, STATUS_HISTORY_SIZE)));
        }

        List<PublicStatusPageComponentResponse> publicComponents = new ArrayList<>(components.size());
        for (StatusPageComponent component : components) {
            ComponentStatus status = componentStatus(component.getLinkedHealthCheckId(), recentHistory);
            publicComponents.add(PublicStatusPageComponentResponse.builder()
                    .name(component.getName())
                    .status(status)
                    .build());
        }

        return PublicStatusPageResponse.builder()
                .title(page.getTitle())
                .description(page.getDescription())
                .overallStatus(overallStatus(publicComponents))
                .components(publicComponents)
                .updatedAt(page.getUpdatedAt())
                .recentOutages(recentOutages(components, now))
                .build();
    }

    private ComponentStatus componentStatus(Long linkedHealthCheckId,
                                            Map<Long, List<HealthCheckResult>> recentHistory) {
        if (linkedHealthCheckId == null) {
            return ComponentStatus.UNKNOWN;
        }
        List<HealthCheckResult> history = recentHistory.get(linkedHealthCheckId);
        if (history == null || history.isEmpty()) {
            return ComponentStatus.UNKNOWN;
        }
        if (isFailure(history.get(0))) {
            return ComponentStatus.DOWN;
        }
        boolean recentFailure = history.stream().skip(1).anyMatch(StatusPageStatusService::isFailure);
        return recentFailure ? ComponentStatus.DEGRADED : ComponentStatus.UP;
    }

    private ComponentStatus overallStatus(List<PublicStatusPageComponentResponse> components) {
        if (components.isEmpty()) {
            return ComponentStatus.UNKNOWN;
        }
        boolean degraded = false;
        boolean up = false;
        for (PublicStatusPageComponentResponse component : components) {
            if (component.getStatus() == ComponentStatus.DOWN) {
                return ComponentStatus.DOWN;
            }
            if (component.getStatus() == ComponentStatus.DEGRADED) {
                degraded = true;
            }
            if (component.getStatus() == ComponentStatus.UP) {
                up = true;
            }
        }
        if (degraded) {
            return ComponentStatus.DEGRADED;
        }
        return up ? ComponentStatus.UP : ComponentStatus.UNKNOWN;
    }

    private List<PublicStatusPageOutageResponse> recentOutages(List<StatusPageComponent> components,
                                                               Instant now) {
        Set<Long> linkedIds = linkedCheckIds(components);
        if (linkedIds.isEmpty()) {
            return List.of();
        }

        Instant since = now.minus(OUTAGE_WINDOW);
        Map<Long, List<HealthCheckResult>> historyByCheck = new HashMap<>();
        for (HealthCheckResult result : resultRepository
                .findByConfig_IdInAndCheckedAtGreaterThanEqualOrderByCheckedAtAscIdAsc(linkedIds, since)) {
            historyByCheck.computeIfAbsent(result.getConfig().getId(), ignored -> new ArrayList<>())
                    .add(result);
        }

        List<PublicStatusPageOutageResponse> outages = new ArrayList<>();
        for (StatusPageComponent component : components) {
            Long configId = component.getLinkedHealthCheckId();
            if (configId == null) {
                continue;
            }
            collectOutages(component.getName(), historyByCheck.get(configId), outages);
        }

        return outages.stream()
                .sorted(Comparator.comparing(PublicStatusPageOutageResponse::getStartedAt).reversed())
                .limit(MAX_OUTAGES)
                .toList();
    }

    private void collectOutages(String componentName,
                                List<HealthCheckResult> history,
                                List<PublicStatusPageOutageResponse> outages) {
        if (history == null || history.isEmpty()) {
            return;
        }
        Instant startedAt = null;
        for (HealthCheckResult result : history) {
            boolean failure = isFailure(result);
            if (failure && startedAt == null) {
                startedAt = result.getCheckedAt();
            } else if (!failure && startedAt != null) {
                outages.add(outage(componentName, startedAt, result.getCheckedAt()));
                startedAt = null;
            }
        }
        if (startedAt != null) {
            outages.add(outage(componentName, startedAt, null));
        }
    }

    private PublicStatusPageOutageResponse outage(String componentName, Instant startedAt, Instant resolvedAt) {
        return PublicStatusPageOutageResponse.builder()
                .componentName(componentName)
                .title(componentName + " was unavailable")
                .startedAt(startedAt)
                .resolvedAt(resolvedAt)
                .build();
    }

    private static Set<Long> linkedCheckIds(List<StatusPageComponent> components) {
        Set<Long> ids = new LinkedHashSet<>();
        for (StatusPageComponent component : components) {
            if (component.getLinkedHealthCheckId() != null) {
                ids.add(component.getLinkedHealthCheckId());
            }
        }
        return ids;
    }

    private static boolean isFailure(HealthCheckResult result) {
        return result.getStatus() != HealthStatus.UP;
    }
}
