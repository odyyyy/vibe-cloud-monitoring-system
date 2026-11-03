package com.monitoring.collector.repository;

import com.monitoring.collector.entity.AgentConfigAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentConfigAssignmentRepository extends JpaRepository<AgentConfigAssignment, Long> {

    Optional<AgentConfigAssignment> findByApplicationId(Long applicationId);

    List<AgentConfigAssignment> findByTemplateId(Long templateId);

    void deleteByApplicationId(Long applicationId);
}
