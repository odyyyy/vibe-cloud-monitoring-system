package com.monitoring.statuspage.repository;

import com.monitoring.statuspage.entity.StatusPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StatusPageRepository extends JpaRepository<StatusPage, Long> {

    @Query("""
            SELECT DISTINCT p FROM StatusPage p
            LEFT JOIN FETCH p.components
            WHERE p.organizationId = :organizationId
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<StatusPage> findAllWithComponentsByOrganizationId(@Param("organizationId") Long organizationId);

    @Query("""
            SELECT DISTINCT p FROM StatusPage p
            LEFT JOIN FETCH p.components
            WHERE p.id = :id
            """)
    Optional<StatusPage> findWithComponentsById(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT p FROM StatusPage p
            LEFT JOIN FETCH p.components
            WHERE p.slug = :slug AND p.active = true
            """)
    Optional<StatusPage> findActiveWithComponentsBySlug(@Param("slug") String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    long countByOrganizationId(Long organizationId);
}
