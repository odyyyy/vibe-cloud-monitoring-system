package com.monitoring.healthcheck.repository;

import com.monitoring.healthcheck.entity.HealthCheckResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HealthCheckResultRepository extends JpaRepository<HealthCheckResult, Long> {

    List<HealthCheckResult> findByConfig_IdOrderByCheckedAtDesc(Long configId, Pageable pageable);

    long countByConfig_Id(Long configId);

    void deleteByConfig_Id(Long configId);
}
