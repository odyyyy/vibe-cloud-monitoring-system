package com.monitoring.dashboards.repository;

import com.monitoring.dashboards.entity.Widget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WidgetRepository extends JpaRepository<Widget, Long> {

    Optional<Widget> findByIdAndDashboard_Id(Long id, Long dashboardId);

    long countByDashboard_Id(Long dashboardId);

    /** Deterministic order used for snapshots, matching the dashboard render order. */
    List<Widget> findByDashboard_IdOrderByPositionYAscPositionXAscIdAsc(Long dashboardId);
}
