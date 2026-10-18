package com.monitoring.healthcheck.service;

import com.monitoring.healthcheck.dto.CreateHealthCheckRequest;
import com.monitoring.healthcheck.dto.HealthCheckResponse;
import com.monitoring.healthcheck.dto.HealthCheckResultResponse;
import com.monitoring.healthcheck.dto.UpdateHealthCheckRequest;
import com.monitoring.healthcheck.entity.HealthCheckConfig;
import com.monitoring.healthcheck.entity.HealthCheckResult;
import com.monitoring.healthcheck.exception.HealthCheckAccessDeniedException;
import com.monitoring.healthcheck.exception.HealthCheckNotFoundException;
import com.monitoring.healthcheck.repository.HealthCheckConfigRepository;
import com.monitoring.healthcheck.repository.HealthCheckResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Application service for synthetic health checks.
 *
 * <p>Every operation is scoped to the authenticated {@code organizationId}: a check that exists but
 * belongs to another user raises {@link HealthCheckAccessDeniedException} (HTTP 403), while a
 * missing one raises {@link HealthCheckNotFoundException} (HTTP 404).
 *
 * <p>Target URLs are validated for SSRF safety before they are stored and again right before
 * each execution ({@link SsrfGuard}).
 */
@Service
@RequiredArgsConstructor
public class HealthCheckService {

    public static final int DEFAULT_RESULTS_LIMIT = 50;
    public static final int MAX_RESULTS_LIMIT = 500;
    public static final int DEFAULT_EXPECTED_STATUS_CODE = 200;
    public static final int DEFAULT_INTERVAL_SECONDS = 60;

    private final HealthCheckConfigRepository configRepository;
    private final HealthCheckResultRepository resultRepository;
    private final SsrfGuard ssrfGuard;

    @Transactional(readOnly = true)
    public List<HealthCheckResponse> list(Long organizationId) {
        return configRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(HealthCheckResponse::fromEntity)
                .toList();
    }

    @Transactional
    public HealthCheckResponse create(Long organizationId, CreateHealthCheckRequest request) {
        URI target = ssrfGuard.validate(request.getTargetUrl().trim());

        HealthCheckConfig config = HealthCheckConfig.builder()
                .organizationId(organizationId)
                .name(request.getName().trim())
                .targetUrl(target.toString())
                .method(request.getMethod())
                .expectedStatusCode(request.getExpectedStatusCode() != null
                        ? request.getExpectedStatusCode()
                        : DEFAULT_EXPECTED_STATUS_CODE)
                .intervalSeconds(request.getIntervalSeconds() != null
                        ? request.getIntervalSeconds()
                        : DEFAULT_INTERVAL_SECONDS)
                .active(request.getActive() == null || request.getActive())
                .build();

        return HealthCheckResponse.fromEntity(configRepository.save(config));
    }

    @Transactional
    public HealthCheckResponse update(Long organizationId, Long id, UpdateHealthCheckRequest request) {
        HealthCheckConfig config = getOwned(organizationId, id);

        if (request.getName() != null && !request.getName().isBlank()) {
            config.setName(request.getName().trim());
        }
        if (request.getTargetUrl() != null && !request.getTargetUrl().isBlank()) {
            URI target = ssrfGuard.validate(request.getTargetUrl().trim());
            config.setTargetUrl(target.toString());
        }
        if (request.getMethod() != null) {
            config.setMethod(request.getMethod());
        }
        if (request.getExpectedStatusCode() != null) {
            config.setExpectedStatusCode(request.getExpectedStatusCode());
        }
        if (request.getIntervalSeconds() != null) {
            config.setIntervalSeconds(request.getIntervalSeconds());
        }
        if (request.getActive() != null) {
            config.setActive(request.getActive());
        }

        return HealthCheckResponse.fromEntity(configRepository.save(config));
    }

    @Transactional
    public void delete(Long organizationId, Long id) {
        HealthCheckConfig config = getOwned(organizationId, id);
        resultRepository.deleteByConfig_Id(id);
        configRepository.delete(config);
    }

    @Transactional(readOnly = true)
    public List<HealthCheckResultResponse> listResults(Long organizationId, Long id, Integer limit) {
        getOwned(organizationId, id);
        int effectiveLimit = limit == null ? DEFAULT_RESULTS_LIMIT : limit;
        effectiveLimit = Math.max(1, Math.min(effectiveLimit, MAX_RESULTS_LIMIT));
        return resultRepository
                .findByConfig_IdOrderByCheckedAtDesc(id, PageRequest.of(0, effectiveLimit)).stream()
                .map(HealthCheckResultResponse::fromEntity)
                .toList();
    }

    /**
     * Persists the outcome of a scheduled check and records the check time for scheduling.
     * Called by {@link HealthCheckScheduler}; not exposed through the API.
     */
    @Transactional
    public void recordResult(Long configId, HealthCheckOutcome outcome, Instant checkedAt) {
        HealthCheckConfig config = configRepository.findById(configId).orElse(null);
        if (config == null) {
            return;
        }
        HealthCheckResult result = HealthCheckResult.builder()
                .config(config)
                .status(outcome.status())
                .responseTimeMs(outcome.responseTimeMs())
                .errorMessage(outcome.errorMessage())
                .checkedAt(checkedAt)
                .build();
        resultRepository.save(result);

        config.setLastCheckedAt(checkedAt);
        configRepository.save(config);
    }

    private HealthCheckConfig getOwned(Long organizationId, Long id) {
        HealthCheckConfig config = configRepository.findById(id)
                .orElseThrow(() -> new HealthCheckNotFoundException(id));
        if (!config.getOrganizationId().equals(organizationId)) {
            throw new HealthCheckAccessDeniedException();
        }
        return config;
    }
}
