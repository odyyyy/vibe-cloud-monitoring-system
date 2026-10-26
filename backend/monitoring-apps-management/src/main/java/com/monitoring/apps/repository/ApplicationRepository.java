package com.monitoring.apps.repository;

import com.monitoring.apps.entity.Application;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

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
     * Bounded, tenant-scoped name search used by the global command palette.
     *
     * <p>{@code LOCATE} is used instead of {@code LIKE} so user-supplied {@code %} and {@code _}
     * characters are always treated literally. Prefix matches are ranked first.
     */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "2000"))
    @Query("""
            SELECT a FROM Application a
            WHERE a.organizationId = :organizationId
              AND LOCATE(:needle, LOWER(a.name)) > 0
            ORDER BY CASE WHEN LOCATE(:needle, LOWER(a.name)) = 1 THEN 0 ELSE 1 END,
                     LOWER(a.name), a.id
            """)
    List<Application> searchByName(@Param("organizationId") Long organizationId,
                                   @Param("needle") String needle,
                                   Pageable pageable);

    /**
     * Distinct organizations that own at least one application.
     *
     * <p>Used by the background usage rollup to enumerate the tenants that can have telemetry,
     * without scanning organizations that never created an application.
     */
    @Query("SELECT DISTINCT a.organizationId FROM Application a")
    List<Long> findDistinctOrganizationIds();
}
