package com.monitoring.revisions.repository;

import com.monitoring.revisions.entity.ConfigResourceType;
import com.monitoring.revisions.entity.ConfigRevisionCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Access to the revision number counters.
 *
 * <p>The counter row is the lock that serializes the writers of one resource: the increment is
 * a single atomic statement and its row lock is held until the surrounding transaction ends.
 */
public interface ConfigRevisionCounterRepository extends JpaRepository<ConfigRevisionCounter, Long> {

    /** Hands out the next revision number of a resource. Returns 0 if the counter is missing. */
    @Modifying
    @Query("""
            update ConfigRevisionCounter c
            set c.lastNumber = c.lastNumber + 1
            where c.organizationId = :organizationId
              and c.resourceType = :resourceType
              and c.resourceId = :resourceId
            """)
    int increment(@Param("organizationId") Long organizationId,
                  @Param("resourceType") ConfigResourceType resourceType,
                  @Param("resourceId") Long resourceId);

    /** Reads the number the current transaction allocated. */
    @Query("""
            select c.lastNumber from ConfigRevisionCounter c
            where c.organizationId = :organizationId
              and c.resourceType = :resourceType
              and c.resourceId = :resourceId
            """)
    Integer findLastNumber(@Param("organizationId") Long organizationId,
                           @Param("resourceType") ConfigResourceType resourceType,
                           @Param("resourceId") Long resourceId);

    /**
     * Creates the counter of a resource. The statement fails with a duplicate key when another
     * writer created the counter first.
     *
     * <p>The counter starts at zero: the caller raises it to the newest revision of the
     * resource. Reading the history is deliberately not part of this statement, because a
     * reading transaction would lock history rows that a concurrent writer prunes.
     */
    @Modifying
    @Query(value = """
            insert into config_revision_counters (organization_id, resource_type, resource_id, last_number)
            values (:organizationId, :resourceType, :resourceId, 0)
            """, nativeQuery = true)
    int insertCounter(@Param("organizationId") Long organizationId,
                      @Param("resourceType") String resourceType,
                      @Param("resourceId") Long resourceId);

    /** Raises the counter to a higher number; a counter that is already ahead stays untouched. */
    @Modifying
    @Query("""
            update ConfigRevisionCounter c
            set c.lastNumber = :lastNumber
            where c.organizationId = :organizationId
              and c.resourceType = :resourceType
              and c.resourceId = :resourceId
              and c.lastNumber < :lastNumber
            """)
    int raiseTo(@Param("organizationId") Long organizationId,
                @Param("resourceType") ConfigResourceType resourceType,
                @Param("resourceId") Long resourceId,
                @Param("lastNumber") int lastNumber);

    /** Removes the counter of a resource. */
    @Modifying
    @Query("""
            delete from ConfigRevisionCounter c
            where c.organizationId = :organizationId
              and c.resourceType = :resourceType
              and c.resourceId = :resourceId
            """)
    int deleteCounter(@Param("organizationId") Long organizationId,
                      @Param("resourceType") ConfigResourceType resourceType,
                      @Param("resourceId") Long resourceId);
}
