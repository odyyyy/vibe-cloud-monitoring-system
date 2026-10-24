package com.monitoring.alerts.dto.response;

import com.monitoring.alerts.entity.NotificationDeliveryStatus;
import com.monitoring.alerts.entity.ScheduledReportChannelType;
import com.monitoring.alerts.service.ScheduledReportContent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * The exact payload that a test report attempted to deliver, plus aggregated data for a
 * structured preview. A failed delivery still returns the rendered payload so the user can see
 * what would have been sent.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledReportPreviewResponse {

    private Long reportId;
    private String reportName;
    private ScheduledReportChannelType channelType;
    private NotificationDeliveryStatus deliveryStatus;
    private String deliveryMessage;
    private Long deliveryId;
    private Integer lastHttpStatus;
    private String subject;
    private String html;
    private Map<String, Object> slackMessage;
    private ScheduledReportContent content;
}
