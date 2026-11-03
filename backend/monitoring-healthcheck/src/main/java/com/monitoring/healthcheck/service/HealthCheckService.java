package com.monitoring.healthcheck.service;

import com.monitoring.healthcheck.dto.SyntheticApiStep;
import com.monitoring.healthcheck.dto.request.CreateHealthCheckRequest;
import com.monitoring.healthcheck.dto.response.HealthCheckResponse;
import com.monitoring.healthcheck.dto.response.HealthCheckResultResponse;
import com.monitoring.healthcheck.dto.request.UpdateHealthCheckRequest;
import com.monitoring.healthcheck.entity.HealthCheckConfig;
import com.monitoring.healthcheck.entity.HealthCheckMethod;
import com.monitoring.healthcheck.entity.HealthCheckResult;
import com.monitoring.healthcheck.entity.HealthCheckType;
import com.monitoring.healthcheck.exception.HealthCheckAccessDeniedException;
import com.monitoring.healthcheck.exception.HealthCheckNotFoundException;
import com.monitoring.healthcheck.exception.InvalidTargetUrlException;
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
 * <p>Target URLs — including every URL in a multi-step workflow — are validated for SSRF safety
 * before they are stored and again right before each execution ({@link SsrfGuard}).
 */
@Service
@RequiredArgsConstructor
public class HealthCheckService {

    public static final int DEFAULT_RESULTS_LIMIT = 50;
    public static final int MAX_RESULTS_LIMIT = 500;
    public static final int DEFAULT_EXPECTED_STATUS_CODE = 200;
    public static final int DEFAULT_INTERVAL_SECONDS = 60;
    private static final HealthCheckMethod DEFAULT_METHOD = HealthCheckMethod.GET;

    private final HealthCheckConfigRepository configRepository;
    private final HealthCheckResultRepository resultRepository;
    private final SsrfGuard ssrfGuard;
    private final SyntheticCheckDefinitionValidator definitionValidator;
    private final SyntheticCheckCodec codec;

    @Transactional(readOnly = true)
    public List<HealthCheckResponse> list(Long organizationId) {
        return configRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(config -> HealthCheckResponse.fromEntity(config, codec))
                .toList();
    }

    @Transactional
    public HealthCheckResponse create(Long organizationId, CreateHealthCheckRequest request) {
        HealthCheckType type = request.getType() == null
                ? HealthCheckType.SINGLE_HTTP
                : request.getType();

        HealthCheckConfig.HealthCheckConfigBuilder builder = HealthCheckConfig.builder()
                .organizationId(organizationId)
                .name(request.getName().trim())
                .type(type)
                .method(request.getMethod() != null ? request.getMethod() : DEFAULT_METHOD)
                .expectedStatusCode(request.getExpectedStatusCode() != null
                        ? request.getExpectedStatusCode()
                        : DEFAULT_EXPECTED_STATUS_CODE)
                .intervalSeconds(request.getIntervalSeconds() != null
                        ? request.getIntervalSeconds()
                        : DEFAULT_INTERVAL_SECONDS)
                .active(request.getActive() == null || request.getActive());

        if (type == HealthCheckType.MULTI_STEP_API) {
            List<SyntheticApiStep> steps = definitionValidator.validate(request.getSteps());
            builder.stepsJson(codec.writeSteps(steps));
        } else {
            rejectStepsForSingleCheck(request.getSteps());
            builder.targetUrl(requireTargetUrl(request.getTargetUrl()));
        }

        return HealthCheckResponse.fromEntity(configRepository.save(builder.build()), codec);
    }

    @Transactional
    public HealthCheckResponse update(Long organizationId, Long id, UpdateHealthCheckRequest request) {
        HealthCheckConfig config = getOwned(organizationId, id);
        HealthCheckType currentType = config.getType() == null
                ? HealthCheckType.SINGLE_HTTP
                : config.getType();
        HealthCheckType targetType = request.getType() == null ? currentType : request.getType();

        if (request.getName() != null && !request.getName().isBlank()) {
            config.setName(request.getName().trim());
        }
        config.setType(targetType);
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

        if (targetType == HealthCheckType.MULTI_STEP_API) {
            List<SyntheticApiStep> steps;
            if (request.getSteps() != null) {
                steps = definitionValidator.validate(request.getSteps());
            } else if (currentType == HealthCheckType.MULTI_STEP_API) {
                steps = codec.readSteps(config.getStepsJson());
                if (steps.isEmpty()) {
                    throw new InvalidTargetUrlException(
                            "A multi-step check must define at least one step");
                }
            } else {
                throw new InvalidTargetUrlException(
                        "A multi-step check must define at least one step");
            }
            config.setStepsJson(codec.writeSteps(steps));
            config.setTargetUrl(null);
        } else {
            rejectStepsForSingleCheck(request.getSteps());
            if (request.getTargetUrl() != null && !request.getTargetUrl().isBlank()) {
                config.setTargetUrl(requireTargetUrl(request.getTargetUrl()));
            } else if (config.getTargetUrl() == null || config.getTargetUrl().isBlank()) {
                throw new InvalidTargetUrlException("Target URL is required");
            }
            config.setStepsJson(null);
        }

        return HealthCheckResponse.fromEntity(configRepository.save(config), codec);
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
                .map(result -> HealthCheckResultResponse.fromEntity(result, codec))
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
                .stepResults(outcome.stepResults().isEmpty()
                        ? null
                        : codec.writeStepResults(outcome.stepResults()))
                .build();
        resultRepository.save(result);

        config.setLastCheckedAt(checkedAt);
        configRepository.save(config);
    }

    private void rejectStepsForSingleCheck(List<SyntheticApiStep> steps) {
        if (steps != null && !steps.isEmpty()) {
            throw new InvalidTargetUrlException(
                    "steps are only supported for multi-step API checks");
        }
    }

    private String requireTargetUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidTargetUrlException("Target URL is required");
        }
        URI target = ssrfGuard.validate(rawUrl.trim());
        return target.toString();
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
