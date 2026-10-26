package com.monitoring.alerts.repository;

import com.monitoring.alerts.entity.AlertRule;
import com.monitoring.alerts.entity.AlertRuleType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

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

    /** Bounded, tenant-scoped name search used by the global command palette. */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "2000"))
    @Query("""
            SELECT r FROM AlertRule r
            WHERE r.organizationId = :organizationId
              AND LOCATE(:needle, LOWER(r.name)) > 0
            ORDER BY CASE WHEN LOCATE(:needle, LOWER(r.name)) = 1 THEN 0 ELSE 1 END,
                     LOWER(r.name), r.id
            """)
    List<AlertRule> searchByName(@Param("organizationId") Long organizationId,
                                 @Param("needle") String needle,
                                 Pageable pageable);
}
