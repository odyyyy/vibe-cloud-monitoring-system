package com.monitoring.collector.repository;

import com.monitoring.collector.entity.SavedLogView;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SavedLogViewRepository extends JpaRepository<SavedLogView, Long> {

    List<SavedLogView> findByOrganizationIdOrderByCreatedAtDescIdDesc(Long organizationId);

    long countByOrganizationId(Long organizationId);

    /** Bounded, tenant-scoped name search used by the global command palette. */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "2000"))
    @Query("""
            SELECT v FROM SavedLogView v
            WHERE v.organizationId = :organizationId
              AND LOCATE(:needle, LOWER(v.name)) > 0
            ORDER BY CASE WHEN LOCATE(:needle, LOWER(v.name)) = 1 THEN 0 ELSE 1 END,
                     LOWER(v.name), v.id
            """)
    List<SavedLogView> searchByName(@Param("organizationId") Long organizationId,
                                    @Param("needle") String needle,
                                    Pageable pageable);
}
