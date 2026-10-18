package com.monitoring.auth.organization.repository;

import com.monitoring.auth.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
}
