package com.monitoring.alerts.repository;

import com.monitoring.alerts.entity.AlertIncident;
import com.monitoring.alerts.entity.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertIncidentRepository extends JpaRepository<AlertIncident, Long> {

    Page<AlertIncident> findByOwnerIdOrderByTriggeredAtDesc(Long ownerId, Pageable pageable);

    Optional<AlertIncident> findFirstByRule_IdAndStatusOrderByTriggeredAtDesc(Long ruleId, IncidentStatus status);

    List<AlertIncident> findByRule_IdAndStatus(Long ruleId, IncidentStatus status);
}
