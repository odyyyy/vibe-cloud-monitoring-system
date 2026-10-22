package com.monitoring.healthcheck.repository;

import com.monitoring.healthcheck.entity.HealthCheckResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
