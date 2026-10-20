package com.monitoring.organization.repository;

import com.monitoring.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    /** The hidden tenant that owns platform self-monitoring data, if it has been created. */
    Optional<Organization> findFirstByIsSystemTrue();
}
