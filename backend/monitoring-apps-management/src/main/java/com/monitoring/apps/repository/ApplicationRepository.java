package com.monitoring.apps.repository;

import com.monitoring.apps.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByApiKeyHash(String apiKeyHash);

    Optional<Application> findByApiKeyHash(String apiKeyHash);

    /**
     * Applications are identified by name only within a single owner, so two tenants may reuse
     * the same name (for example "prod") without colliding or learning about each other.
     */
    boolean existsByNameAndOrganizationId(String name, Long organizationId);

    List<Application> findByOrganizationId(Long organizationId);

    /** The internal application that stores the platform's own telemetry. */
    Optional<Application> findFirstByIsSystemTrue();

    /**
     * Distinct organizations that own at least one application.
     *
     * <p>Used by the background usage rollup to enumerate the tenants that can have telemetry,
     * without scanning organizations that never created an application.
     */
    @Query("SELECT DISTINCT a.organizationId FROM Application a")
    List<Long> findDistinctOrganizationIds();
}
