package com.monitoring.alerts.service;

import com.monitoring.alerts.dto.request.CreateMaintenanceWindowRequest;
import com.monitoring.alerts.dto.request.UpdateMaintenanceWindowRequest;
import com.monitoring.alerts.dto.response.MaintenanceWindowResponse;
import com.monitoring.alerts.entity.MaintenanceScopeType;
import com.monitoring.alerts.entity.MaintenanceWindow;
import com.monitoring.alerts.exception.InvalidMaintenanceWindowException;
import com.monitoring.alerts.exception.MaintenanceWindowAccessDeniedException;
import com.monitoring.alerts.exception.MaintenanceWindowNotFoundException;
import com.monitoring.alerts.repository.AlertRuleRepository;
import com.monitoring.alerts.repository.MaintenanceWindowRepository;
import com.monitoring.apps.exception.ApplicationAccessDeniedException;
import com.monitoring.apps.exception.ApplicationNotFoundException;
import com.monitoring.apps.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Application service for alert maintenance windows.
 *
 * <p>Windows are tenant-owned: every read and write is scoped to the authenticated
 * {@code organizationId}. A window that exists but belongs to another tenant raises
 * {@link MaintenanceWindowAccessDeniedException}, while a missing one raises
 * {@link MaintenanceWindowNotFoundException}.
 *
 * <p>All times are UTC instants. A window may start up to one hour in the past (retroactive
 * muting) but must end in the future. Evaluation treats the interval as
 * {@code [startTime, endTime)}.
 */
@Service
@RequiredArgsConstructor
public class MaintenanceWindowService {

    /** Tolerated backdating so a user can mute an alert that just started firing. */
    static final Duration MAX_BACKDATING = Duration.ofHours(1);

    private final MaintenanceWindowRepository windowRepository;
    private final AlertRuleRepository ruleRepository;
    private final ApplicationService applicationService;

    @Transactional(readOnly = true)
    public List<MaintenanceWindowResponse> list(Long organizationId) {
        return windowRepository.findByOrganizationIdOrderByStartTimeDesc(organizationId).stream()
                .map(MaintenanceWindowResponse::fromEntity)
                .toList();
    }

    @Transactional
    public MaintenanceWindowResponse create(Long organizationId, CreateMaintenanceWindowRequest request) {
        MaintenanceScopeType scopeType = request.getScopeType();
        if (scopeType == null) {
            throw new InvalidMaintenanceWindowException("scopeType is required");
        }
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new InvalidMaintenanceWindowException("startTime and endTime are required");
        }

        Long scopeId = normalizeScopeId(organizationId, scopeType, request.getScopeId());
        Instant now = Instant.now();
        validateTimes(request.getStartTime(), request.getEndTime(), now, true, true);

        MaintenanceWindow window = MaintenanceWindow.builder()
                .organizationId(organizationId)
                .name(request.getName().trim())
                .scopeType(scopeType)
                .scopeId(scopeId)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .active(request.getActive() == null || request.getActive())
                .build();

        return MaintenanceWindowResponse.fromEntity(windowRepository.save(window));
    }

    @Transactional
    public MaintenanceWindowResponse update(
            Long organizationId, Long id, UpdateMaintenanceWindowRequest request) {
        MaintenanceWindow window = getOwnedWindow(organizationId, id);

        if (request.getName() != null) {
            String name = request.getName().trim();
            if (name.isEmpty()) {
                throw new InvalidMaintenanceWindowException("name must not be blank");
            }
            window.setName(name);
        }

        MaintenanceScopeType scopeType = request.getScopeType() != null
                ? request.getScopeType()
                : window.getScopeType();
        Long scopeId;
        if (request.getScopeType() != null && request.getScopeType() != window.getScopeType()) {
            scopeId = normalizeScopeId(organizationId, scopeType, request.getScopeId());
        } else if (request.getScopeId() != null) {
            scopeId = normalizeScopeId(organizationId, scopeType, request.getScopeId());
        } else {
            scopeId = scopeType == MaintenanceScopeType.GLOBAL ? null : window.getScopeId();
        }

        boolean startChanged = request.getStartTime() != null
                && !request.getStartTime().equals(window.getStartTime());
        boolean endChanged = request.getEndTime() != null
                && !request.getEndTime().equals(window.getEndTime());
        Instant startTime = startChanged ? request.getStartTime() : window.getStartTime();
        Instant endTime = endChanged ? request.getEndTime() : window.getEndTime();
        if (startChanged || endChanged) {
            validateTimes(startTime, endTime, Instant.now(), startChanged, endChanged);
        }

        window.setScopeType(scopeType);
        window.setScopeId(scopeId);
        window.setStartTime(startTime);
        window.setEndTime(endTime);
        if (request.getActive() != null) {
            window.setActive(request.getActive());
        }

        return MaintenanceWindowResponse.fromEntity(windowRepository.save(window));
    }

    @Transactional
    public void delete(Long organizationId, Long id) {
        windowRepository.delete(getOwnedWindow(organizationId, id));
    }

    /**
     * True when an active window of the rule's organization covers the evaluation instant and
     * applies to the rule through its global, application or rule scope.
     */
    @Transactional(readOnly = true)
    public boolean isMuted(Long organizationId, Long applicationId, Long ruleId, Instant now) {
        if (organizationId == null || now == null) {
            return false;
        }
        List<MaintenanceWindow> windows = windowRepository
                .findByOrganizationIdAndActiveTrueAndStartTimeLessThanEqualAndEndTimeAfter(
                        organizationId, now, now);
        for (MaintenanceWindow window : windows) {
            switch (window.getScopeType()) {
                case GLOBAL -> {
                    return true;
                }
                case APPLICATION -> {
                    if (applicationId != null && applicationId.equals(window.getScopeId())) {
                        return true;
                    }
                }
                case SPECIFIC_RULE -> {
                    if (ruleId != null && ruleId.equals(window.getScopeId())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private MaintenanceWindow getOwnedWindow(Long organizationId, Long id) {
        MaintenanceWindow window = windowRepository.findById(id)
                .orElseThrow(() -> new MaintenanceWindowNotFoundException(id));
        if (!window.getOrganizationId().equals(organizationId)) {
            throw new MaintenanceWindowAccessDeniedException();
        }
        return window;
    }

    /**
     * Validates the scope reference against the tenant and returns the scope id to store.
     * A global window has no scope id; the other scopes require an owned resource.
     */
    private Long normalizeScopeId(
            Long organizationId, MaintenanceScopeType scopeType, Long scopeId) {
        if (scopeType == MaintenanceScopeType.GLOBAL) {
            if (scopeId != null) {
                throw new InvalidMaintenanceWindowException("A global window does not take a scopeId");
            }
            return null;
        }
        if (scopeId == null) {
            throw new InvalidMaintenanceWindowException(
                    "scopeId is required for an application or rule window");
        }

        if (scopeType == MaintenanceScopeType.APPLICATION) {
            try {
                applicationService.getOwnedApplication(scopeId, organizationId);
            } catch (ApplicationNotFoundException | ApplicationAccessDeniedException ex) {
                throw new InvalidMaintenanceWindowException(
                        "The referenced application does not exist or is not yours");
            }
            return scopeId;
        }

        ruleRepository.findByIdAndOrganizationId(scopeId, organizationId)
                .orElseThrow(() -> new InvalidMaintenanceWindowException(
                        "The referenced alert rule does not exist or is not yours"));
        return scopeId;
    }

    private void validateTimes(
            Instant startTime, Instant endTime, Instant now, boolean startChanged, boolean endChanged) {
        if (startChanged && startTime.isBefore(now.minus(MAX_BACKDATING))) {
            throw new InvalidMaintenanceWindowException(
                    "startTime cannot be more than 1 hour in the past");
        }
        if (endChanged && !endTime.isAfter(now)) {
            throw new InvalidMaintenanceWindowException("endTime must be in the future");
        }
        if ((startChanged || endChanged) && !endTime.isAfter(startTime)) {
            throw new InvalidMaintenanceWindowException("endTime must be after startTime");
        }
    }
}
