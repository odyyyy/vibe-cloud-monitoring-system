package com.monitoring.alerts.repository;

import com.monitoring.alerts.entity.AlertIncident;
import com.monitoring.alerts.entity.IncidentStatus;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlertIncidentRepository extends JpaRepository<AlertIncident, Long> {

    Page<AlertIncident> findByOrganizationIdOrderByTriggeredAtDesc(Long organizationId, Pageable pageable);

    Page<AlertIncident> findByOrganizationIdAndStatusOrderByTriggeredAtDesc(
            Long organizationId, IncidentStatus status, Pageable pageable);

    Optional<AlertIncident> findFirstByRule_IdAndStatusOrderByTriggeredAtDesc(Long ruleId, IncidentStatus status);

    Optional<AlertIncident> findFirstByRule_IdAndStatusInOrderByTriggeredAtDesc(
            Long ruleId, Collection<IncidentStatus> statuses);

    List<AlertIncident> findByRule_IdAndStatus(Long ruleId, IncidentStatus status);

    List<AlertIncident> findByRule_IdAndStatusIn(Long ruleId, Collection<IncidentStatus> statuses);

    @EntityGraph(attributePaths = "rule")
    List<AlertIncident> findByOrganizationIdAndTriggeredAtGreaterThanEqualAndTriggeredAtLessThanOrderByTriggeredAtDesc(
            Long organizationId, Instant from, Instant to, Pageable pageable);

    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "5000"))
    @Query("SELECT COUNT(i) FROM AlertIncident i"
            + " WHERE i.organizationId = :organizationId"
            + " AND i.triggeredAt >= :from AND i.triggeredAt < :to")
    long countByOrganizationIdAndTimeRange(@Param("organizationId") Long organizationId,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to);

    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "5000"))
    @Query("SELECT COUNT(i) FROM AlertIncident i"
            + " WHERE i.organizationId = :organizationId"
            + " AND i.triggeredAt >= :from AND i.triggeredAt < :to"
            + " AND i.status IN :statuses")
    long countByOrganizationIdAndTimeRangeAndStatusIn(@Param("organizationId") Long organizationId,
                                                      @Param("from") Instant from,
                                                      @Param("to") Instant to,
                                                      @Param("statuses") Collection<IncidentStatus> statuses);

    /**
     * Bounded, tenant-scoped incident search by the owning rule name.
     *
     * <p>The rule is fetched in the same query so the palette mapping does not trigger N+1
     * lookups. {@code LOCATE} keeps user-supplied wildcard characters literal.
     */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "2000"))
    @Query("""
            SELECT i FROM AlertIncident i JOIN FETCH i.rule r
            WHERE i.organizationId = :organizationId
              AND LOCATE(:needle, LOWER(r.name)) > 0
            ORDER BY CASE WHEN LOCATE(:needle, LOWER(r.name)) = 1 THEN 0 ELSE 1 END,
                     LOWER(r.name), i.id
            """)
    List<AlertIncident> searchByRuleName(@Param("organizationId") Long organizationId,
                                         @Param("needle") String needle,
                                         Pageable pageable);
}
