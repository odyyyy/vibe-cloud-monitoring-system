package com.monitoring.collector.repository;

import com.monitoring.collector.entity.AgentConfigTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentConfigTemplateRepository extends JpaRepository<AgentConfigTemplate, Long> {

    List<AgentConfigTemplate> findByOrganizationIdOrderByNameAsc(Long organizationId);

    Optional<AgentConfigTemplate> findByIdAndOrganizationId(Long id, Long organizationId);

    Optional<AgentConfigTemplate> findFirstByOrganizationIdAndIsDefaultTrue(Long organizationId);

    long countByOrganizationId(Long organizationId);

    boolean existsByOrganizationIdAndName(Long organizationId, String name);

    boolean existsByOrganizationIdAndNameAndIdNot(Long organizationId, String name, Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AgentConfigTemplate t
            SET t.isDefault = false
            WHERE t.organizationId = :organizationId
              AND t.isDefault = true
              AND (:keepId IS NULL OR t.id <> :keepId)
            """)
    int clearDefault(@Param("organizationId") Long organizationId, @Param("keepId") Long keepId);
}
