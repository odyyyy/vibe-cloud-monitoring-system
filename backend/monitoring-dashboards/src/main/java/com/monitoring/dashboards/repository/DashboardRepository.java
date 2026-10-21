package com.monitoring.dashboards.repository;

import com.monitoring.dashboards.entity.Dashboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DashboardRepository extends JpaRepository<Dashboard, Long> {

    /**
     * Lists a user's dashboards with their widgets eagerly fetched to avoid the N+1 query
     * that lazy access from the DTO mapping would otherwise cause.
     */
    @Query("""
            SELECT DISTINCT d FROM Dashboard d
            LEFT JOIN FETCH d.widgets
            WHERE d.organizationId = :organizationId
            ORDER BY d.createdAt DESC
            """)
    List<Dashboard> findAllWithWidgetsByOrganizationId(@Param("organizationId") Long organizationId);

    /**
     * Finds a built-in dashboard by its stable type. A unique index guarantees at most one
     * dashboard per type, so the bootstrap can safely be idempotent.
     */
    @Query("SELECT d FROM Dashboard d LEFT JOIN FETCH d.widgets WHERE d.systemDashboardType = :type")
    Optional<Dashboard> findBySystemDashboardType(@Param("type") String type);

    @Query("SELECT d FROM Dashboard d LEFT JOIN FETCH d.widgets WHERE d.id = :id")
    Optional<Dashboard> findWithWidgetsById(@Param("id") Long id);

    /**
     * Finds a dashboard by its public share token with widgets eagerly fetched.
     * Only returns dashboards that are publicly shared (isPublic = true).
     */
    @Query("SELECT d FROM Dashboard d LEFT JOIN FETCH d.widgets WHERE d.shareToken = :token AND d.isPublic = true")
    Optional<Dashboard> findByShareToken(@Param("token") String token);
}
