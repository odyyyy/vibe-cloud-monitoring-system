package com.monitoring.retention.service;

import com.monitoring.retention.config.RetentionProperties;
import com.monitoring.retention.dto.request.UpdateRetentionPolicyRequest;
import com.monitoring.retention.dto.response.RetentionPolicyResponse;
import com.monitoring.retention.entity.RetentionPolicy;
import com.monitoring.retention.entity.RetentionPurgeRun;
import com.monitoring.retention.exception.InvalidRetentionPolicyException;
import com.monitoring.retention.repository.RetentionPolicyRepository;
import com.monitoring.retention.repository.RetentionPurgeRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Reads and updates per-organization retention policies.
 *
 * <p>An organization without a stored policy transparently uses the configured defaults, so
 * the purge job always has a window without requiring setup. Updates are validated against
 * the per-type hard limit; a value that is too large is rejected rather than silently capped,
 * because reducing it later would delete data the user believed was retained.
 */
@Service
@RequiredArgsConstructor
public class RetentionPolicyService {

    private final RetentionPolicyRepository retentionPolicyRepository;
    private final RetentionPurgeRunRepository retentionPurgeRunRepository;
    private final RetentionProperties properties;

    @Transactional(readOnly = true)
    public RetentionPolicyResponse get(Long organizationId) {
        RetentionPolicy policy = retentionPolicyRepository.findByOrganizationId(organizationId)
                .orElse(null);
        return toResponse(organizationId, policy);
    }

    /** The values the purge job must apply to the organization. */
    @Transactional(readOnly = true)
    public EffectiveRetention effective(Long organizationId) {
        return retentionPolicyRepository.findByOrganizationId(organizationId)
                .map(policy -> new EffectiveRetention(
                        policy.getLogsRetentionDays(),
                        policy.getMetricsRetentionDays(),
                        policy.getTracesRetentionDays()))
                .orElseGet(() -> new EffectiveRetention(
                        properties.getDefaultLogsDays(),
                        properties.getDefaultMetricsDays(),
                        properties.getDefaultTracesDays()));
    }

    /** Most recent purge runs of the organization, newest first. */
    @Transactional(readOnly = true)
    public List<RetentionPurgeRun> recentRuns(Long organizationId, int limit) {
        return retentionPurgeRunRepository.findRecent(organizationId, PageRequest.of(0, limit));
    }

    @Transactional
    public RetentionPolicyResponse update(Long organizationId, Long userId,
                                          UpdateRetentionPolicyRequest request) {
        int logs = requireWithinBounds("Logs", request.logsRetentionDays(), properties.getMaxLogsDays());
        int metrics = requireWithinBounds("Metrics", request.metricsRetentionDays(),
                properties.getMaxMetricsDays());
        int traces = requireWithinBounds("Traces", request.tracesRetentionDays(),
                properties.getMaxTracesDays());

        RetentionPolicy policy = retentionPolicyRepository.findByOrganizationId(organizationId)
                .orElseGet(() -> RetentionPolicy.builder().organizationId(organizationId).build());
        policy.setLogsRetentionDays(logs);
        policy.setMetricsRetentionDays(metrics);
        policy.setTracesRetentionDays(traces);
        policy.setUpdatedAt(Instant.now());
        policy.setUpdatedBy(userId);

        return toResponse(organizationId, retentionPolicyRepository.save(policy));
    }

    private RetentionPolicyResponse toResponse(Long organizationId, RetentionPolicy policy) {
        return new RetentionPolicyResponse(
                organizationId,
                policy != null ? policy.getLogsRetentionDays() : properties.getDefaultLogsDays(),
                policy != null ? policy.getMetricsRetentionDays() : properties.getDefaultMetricsDays(),
                policy != null ? policy.getTracesRetentionDays() : properties.getDefaultTracesDays(),
                policy != null ? policy.getUpdatedAt() : null,
                policy != null ? policy.getUpdatedBy() : null,
                properties.getMaxLogsDays(),
                properties.getMaxMetricsDays(),
                properties.getMaxTracesDays());
    }

    private int requireWithinBounds(String label, Integer value, int max) {
        if (value == null || value < 1) {
            throw new InvalidRetentionPolicyException(label + " retention must be at least 1 day");
        }
        if (value > max) {
            throw new InvalidRetentionPolicyException(
                    label + " retention must not exceed " + max + " days");
        }
        return value;
    }
}
