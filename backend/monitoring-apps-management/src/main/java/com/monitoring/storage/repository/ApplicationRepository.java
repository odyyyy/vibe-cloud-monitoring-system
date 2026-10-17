package com.monitoring.storage.repository;

import com.monitoring.storage.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByApiKeyHash(String apiKeyHash);

    Optional<Application> findByApiKeyHash(String apiKeyHash);

    /**
     * Applications are identified by name only within a single owner, so two tenants may reuse
     * the same name (for example "prod") without colliding or learning about each other.
     */
    boolean existsByNameAndOwnerId(String name, Long ownerId);

    List<Application> findByOwnerId(Long ownerId);
}
