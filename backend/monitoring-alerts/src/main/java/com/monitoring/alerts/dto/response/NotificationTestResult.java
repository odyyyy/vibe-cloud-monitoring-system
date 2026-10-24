package com.monitoring.alerts.dto.response;

import com.monitoring.alerts.entity.NotificationDeliveryStatus;

/** Result of a synchronous test-notification request. */
public record NotificationTestResult(
        NotificationDeliveryStatus status,
        String message,
        Integer httpStatus,
        Long deliveryId) {
}
