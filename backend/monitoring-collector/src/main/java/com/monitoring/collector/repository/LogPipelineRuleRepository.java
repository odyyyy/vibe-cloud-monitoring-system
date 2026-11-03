package com.monitoring.collector.repository;

import com.monitoring.collector.entity.LogPipelineRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LogPipelineRuleRepository extends JpaRepository<LogPipelineRule, Long> {

    List<LogPipelineRule> findByOrganizationIdOrderByOrderIndexAscIdAsc(Long organizationId);

    Optional<LogPipelineRule> findByIdAndOrganizationId(Long id, Long organizationId);

    long countByOrganizationId(Long organizationId);

    @Query("SELECT COALESCE(MAX(r.orderIndex), -1) FROM LogPipelineRule r"
            + " WHERE r.organizationId = :organizationId")
    int findMaxOrderIndex(@Param("organizationId") Long organizationId);

    /**
     * Rules that apply to one application: organization-wide rules and rules explicitly scoped
     * to that application. The application is always resolved from the API key, never from the
     * agent request.
     */
    @Query("SELECT r FROM LogPipelineRule r"
            + " WHERE r.organizationId = :organizationId"
            + " AND (r.application IS NULL OR r.application.id = :applicationId)"
            + " ORDER BY r.orderIndex ASC, r.id ASC")
    List<LogPipelineRule> findApplicableRules(@Param("organizationId") Long organizationId,
                                              @Param("applicationId") Long applicationId);

    List<LogPipelineRule> findByOrganizationIdAndIdIn(Long organizationId, List<Long> ids);
}
