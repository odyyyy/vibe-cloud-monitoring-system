package com.monitoring.collector.repository;

import com.monitoring.collector.entity.SavedLogView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavedLogViewRepository extends JpaRepository<SavedLogView, Long> {

    List<SavedLogView> findByOrganizationIdOrderByCreatedAtDescIdDesc(Long organizationId);

    long countByOrganizationId(Long organizationId);
}
