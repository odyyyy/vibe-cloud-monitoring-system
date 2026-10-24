package com.monitoring.alerts.repository;

import com.monitoring.alerts.entity.IncidentComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentCommentRepository extends JpaRepository<IncidentComment, Long> {

    /**
     * Newest first, with the id as a deterministic tie-breaker for comments stored in the same
     * timestamp tick. The organization id is part of the query so tenant scope never depends on
     * a previous lookup alone.
     */
    Page<IncidentComment> findByOrganizationIdAndIncidentIdOrderByCreatedAtDescIdDesc(
            Long organizationId, Long incidentId, Pageable pageable);
}
