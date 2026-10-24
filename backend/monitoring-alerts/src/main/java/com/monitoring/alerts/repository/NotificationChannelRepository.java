package com.monitoring.alerts.repository;

import com.monitoring.alerts.entity.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, Long> {

    List<NotificationChannel> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    Optional<NotificationChannel> findByIdAndOrganizationId(Long id, Long organizationId);

    /** Channels that should receive notifications for a tenant. */
    List<NotificationChannel> findByOrganizationIdAndActiveTrue(Long organizationId);

    /** Number of channels configured by a tenant (backs the per-organization limit). */
    long countByOrganizationId(Long organizationId);
}
