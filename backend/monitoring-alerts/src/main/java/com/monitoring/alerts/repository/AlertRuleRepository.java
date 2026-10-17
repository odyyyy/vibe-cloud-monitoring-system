package com.monitoring.alerts.repository;

import com.monitoring.alerts.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    Optional<AlertRule> findByIdAndOwnerId(Long id, Long ownerId);

    /** Active rules considered by the scheduler. */
    List<AlertRule> findByActiveTrue();
}
