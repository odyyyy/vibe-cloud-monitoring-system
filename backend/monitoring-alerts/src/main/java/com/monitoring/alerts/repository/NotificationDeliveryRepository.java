package com.monitoring.alerts.repository;

import com.monitoring.alerts.entity.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NotificationDeliveryRepository
        extends JpaRepository<NotificationDelivery, Long>, JpaSpecificationExecutor<NotificationDelivery> {
}
