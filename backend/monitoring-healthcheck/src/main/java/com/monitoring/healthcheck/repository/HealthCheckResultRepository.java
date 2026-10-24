package com.monitoring.healthcheck.repository;

import com.monitoring.healthcheck.entity.HealthCheckResult;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface HealthCheckResultRepository extends JpaRepository<HealthCheckResult, Long> {

    List<HealthCheckResult> findByConfig_IdOrderByCheckedAtDesc(Long configId, Pageable pageable);

    List<HealthCheckResult> findByConfig_IdOrderByCheckedAtDescIdDesc(Long configId, Pageable pageable);

    List<HealthCheckResult> findByConfig_IdInAndCheckedAtGreaterThanEqualOrderByCheckedAtAscIdAsc(
            Collection<Long> configIds, Instant since);

    long countByConfig_Id(Long configId);

    void deleteByConfig_Id(Long configId);

    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "5000"))
    @Query("SELECT COUNT(r) FROM HealthCheckResult r"
            + " WHERE r.config.id IN :configIds"
            + " AND r.checkedAt >= :from AND r.checkedAt < :to")
    long countByConfigIdsAndTimeRange(@Param("configIds") Collection<Long> configIds,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to);

    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "5000"))
    @Query("SELECT COUNT(r) FROM HealthCheckResult r"
            + " WHERE r.config.id IN :configIds"
            + " AND r.status = com.monitoring.healthcheck.entity.HealthStatus.UP"
            + " AND r.checkedAt >= :from AND r.checkedAt < :to")
    long countUpByConfigIdsAndTimeRange(@Param("configIds") Collection<Long> configIds,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to);
}
