package com.monitoring.storage.repository;

import com.monitoring.storage.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByApiKey(String apiKey);

    Optional<Application> findByApiKey(String apiKey);

    java.util.List<Application> findByOwnerId(Long ownerId);

    void deleteByApiKey(String apiKey);
}
