package com.monitoring.alerts.repository;

import com.monitoring.alerts.entity.AlertRule;
import com.monitoring.alerts.entity.AlertRuleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    /**
     * Active metric rules scoped to one application, used to overlay alert thresholds on a chart.
     * Metric names are not part of the query: a rule may carry several conditions across metrics,
     * so the caller filters on the embedded condition targets.
     */
    List<AlertRule> findByOrganizationIdAndApplicationIdAndActiveTrueOrderByCreatedAtDesc(
            Long organizationId, Long applicationId);

    Optional<AlertRule> findByIdAndOrganizationId(Long id, Long organizationId);

    /** Active rules considered by the scheduler. */
    List<AlertRule> findByActiveTrue();

    /** Backs the per-organization cap on active log rules. */
    long countByOrganizationIdAndTypeAndActiveTrue(Long organizationId, AlertRuleType type);
}
