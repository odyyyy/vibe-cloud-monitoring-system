package com.monitoring.revisions.repository;

import com.monitoring.revisions.entity.ConfigResourceType;
import com.monitoring.revisions.entity.ConfigRevision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Access to the configuration revision history.
 *
 * <p>History rows are removed with bulk statements. A row-by-row delete would fail with an
 * optimistic locking error when another transaction already removed the same row.
 */
public interface ConfigRevisionRepository extends JpaRepository<ConfigRevision, Long> {

    /** Reads the newest revision of a resource. */
    Optional<ConfigRevision> findTopByOrganizationIdAndResourceTypeAndResourceIdOrderByRevisionNumberDescIdDesc(
            Long organizationId, ConfigResourceType resourceType, Long resourceId);

    /**
     * Reads a single revision. Duplicated revision numbers (written before the history was
     * serialized) resolve to the newest row.
     */
    Optional<ConfigRevision> findFirstByOrganizationIdAndResourceTypeAndResourceIdAndRevisionNumberOrderByIdDesc(
            Long organizationId, ConfigResourceType resourceType, Long resourceId, int revisionNumber);

    Page<ConfigRevision> findByOrganizationIdAndResourceTypeAndResourceId(
            Long organizationId, ConfigResourceType resourceType, Long resourceId, Pageable pageable);

    /** Reads the highest revision number a resource has. */
    @Query("""
            select max(r.revisionNumber) from ConfigRevision r
            where r.organizationId = :organizationId
              and r.resourceType = :resourceType
              and r.resourceId = :resourceId
            """)
    Integer findMaxRevisionNumber(@Param("organizationId") Long organizationId,
                                  @Param("resourceType") ConfigResourceType resourceType,
                                  @Param("resourceId") Long resourceId);

    /** Removes the whole history of one resource. */
    @Modifying
    @Query("""
            delete from ConfigRevision r
            where r.organizationId = :organizationId
              and r.resourceType = :resourceType
              and r.resourceId = :resourceId
            """)
    int deleteResourceHistory(@Param("organizationId") Long organizationId,
                              @Param("resourceType") ConfigResourceType resourceType,
                              @Param("resourceId") Long resourceId);

    /** Removes the revisions of one resource that are older than the given revision number. */
    @Modifying
    @Query("""
            delete from ConfigRevision r
            where r.organizationId = :organizationId
              and r.resourceType = :resourceType
              and r.resourceId = :resourceId
              and r.revisionNumber <= :revisionNumber
            """)
    int deleteResourceHistoryUpTo(@Param("organizationId") Long organizationId,
                                  @Param("resourceType") ConfigResourceType resourceType,
                                  @Param("resourceId") Long resourceId,
                                  @Param("revisionNumber") int revisionNumber);
}
