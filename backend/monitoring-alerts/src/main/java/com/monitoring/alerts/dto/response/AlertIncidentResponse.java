package com.monitoring.alerts.dto.response;

import com.monitoring.alerts.entity.AlertIncident;
import com.monitoring.alerts.entity.IncidentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

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
    private Instant resolvedAt;

    public static AlertIncidentResponse fromEntity(AlertIncident incident) {
        return AlertIncidentResponse.builder()
                .id(incident.getId())
                .ruleId(incident.getRule().getId())
                .ruleName(incident.getRule().getName())
                .status(incident.getStatus())
                .triggeredAt(incident.getTriggeredAt())
                .resolvedAt(incident.getResolvedAt())
                .build();
    }
}
