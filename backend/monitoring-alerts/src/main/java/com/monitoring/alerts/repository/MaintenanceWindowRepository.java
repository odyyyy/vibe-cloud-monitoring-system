package com.monitoring.alerts.repository;

import com.monitoring.alerts.entity.MaintenanceWindow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MaintenanceWindowRepository extends JpaRepository<MaintenanceWindow, Long> {

    List<MaintenanceWindow> findByOrganizationIdOrderByStartTimeDesc(Long organizationId);

    Optional<MaintenanceWindow> findByIdAndOrganizationId(Long id, Long organizationId);

    /**
     * Active windows of one tenant that contain the evaluation instant. The caller matches the
     * scope (global, application or rule) so a single query serves every rule evaluation.
     */
    List<MaintenanceWindow> findByOrganizationIdAndActiveTrueAndStartTimeLessThanEqualAndEndTimeAfter(
            Long organizationId, Instant startTime, Instant endTime);
}
