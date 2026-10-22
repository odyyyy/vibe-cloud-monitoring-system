package com.monitoring.retention.repository;

import com.monitoring.retention.entity.RetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, Long> {

    Optional<RetentionPolicy> findByOrganizationId(Long organizationId);
}
