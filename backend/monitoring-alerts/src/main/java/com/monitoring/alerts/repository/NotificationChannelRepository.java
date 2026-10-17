package com.monitoring.alerts.repository;

import com.monitoring.alerts.entity.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, Long> {

    List<NotificationChannel> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    Optional<NotificationChannel> findByIdAndOwnerId(Long id, Long ownerId);

    /** Channels that should receive notifications for a tenant. */
    List<NotificationChannel> findByOwnerIdAndActiveTrue(Long ownerId);
}
