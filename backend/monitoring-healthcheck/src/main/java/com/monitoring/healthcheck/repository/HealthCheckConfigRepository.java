package com.monitoring.healthcheck.repository;

import com.monitoring.healthcheck.entity.HealthCheckConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HealthCheckConfigRepository extends JpaRepository<HealthCheckConfig, Long> {

    List<HealthCheckConfig> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    Optional<HealthCheckConfig> findByIdAndOrganizationId(Long id, Long organizationId);

    /** Active configurations considered by the scheduler. */
    List<HealthCheckConfig> findByActiveTrue();
}
