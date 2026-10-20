package com.monitoring.alerts.dto.response;

import com.monitoring.alerts.entity.AlertIncident;
import com.monitoring.alerts.entity.IncidentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/** Public representation of an alert incident. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertIncidentResponse {

    private Long id;
    private Long ruleId;
    private String ruleName;
    private IncidentStatus status;
    private Instant triggeredAt;
    private Long acknowledgedBy;
    private String acknowledgedByName;
    private Instant acknowledgedAt;
    private Long resolvedBy;
    private String resolvedByName;
    private Instant resolvedAt;
    private String notes;

    /**
     * @param userNames usernames keyed by user id, used to show who acknowledged/resolved the
     *                  incident without exposing unrelated user data to the caller
     */
    public static AlertIncidentResponse fromEntity(AlertIncident incident, Map<Long, String> userNames) {
        return AlertIncidentResponse.builder()
                .id(incident.getId())
                .ruleId(incident.getRule().getId())
                .ruleName(incident.getRule().getName())
                .status(incident.getStatus())
                .triggeredAt(incident.getTriggeredAt())
                .acknowledgedBy(incident.getAcknowledgedBy())
                .acknowledgedByName(userName(incident.getAcknowledgedBy(), userNames))
                .acknowledgedAt(incident.getAcknowledgedAt())
                .resolvedBy(incident.getResolvedBy())
                .resolvedByName(userName(incident.getResolvedBy(), userNames))
                .resolvedAt(incident.getResolvedAt())
                .notes(incident.getNotes())
                .build();
    }

    private static String userName(Long userId, Map<Long, String> userNames) {
        return userId == null ? null : userNames.get(userId);
    }
}
