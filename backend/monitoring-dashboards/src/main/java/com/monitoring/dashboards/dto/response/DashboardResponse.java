package com.monitoring.dashboards.dto.response;

import com.monitoring.dashboards.dto.DashboardVariable;
import com.monitoring.dashboards.entity.Dashboard;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardResponse {

    private Long id;
    private String name;
    private Long organizationId;
    private Instant createdAt;
    private List<DashboardVariable> variables;
    private List<WidgetResponse> widgets;
    private String shareToken;
    private Boolean isPublic;
    private Boolean isSystem;
    private String systemDashboardType;

    public static DashboardResponse fromEntity(Dashboard dashboard, List<DashboardVariable> variables) {
        return DashboardResponse.builder()
                .id(dashboard.getId())
                .name(dashboard.getName())
                .organizationId(dashboard.getOrganizationId())
                .createdAt(dashboard.getCreatedAt())
                .variables(variables)
                .widgets(dashboard.getWidgets().stream().map(WidgetResponse::fromEntity).toList())
                .shareToken(dashboard.getShareToken())
                .isPublic(dashboard.isPublic())
                .isSystem(dashboard.isSystem())
                .systemDashboardType(dashboard.getSystemDashboardType())
                .build();
    }

    /**
     * Creates a public response without sensitive fields like organizationId.
     * Used for the public share endpoint.
     */
    public static DashboardResponse fromEntityPublic(Dashboard dashboard, List<DashboardVariable> variables) {
        return DashboardResponse.builder()
                .id(dashboard.getId())
                .name(dashboard.getName())
                .organizationId(null) // Not exposed in public view
                .createdAt(dashboard.getCreatedAt())
                .variables(variables)
                .widgets(dashboard.getWidgets().stream().map(WidgetResponse::fromEntity).toList())
                .shareToken(dashboard.getShareToken())
                .isPublic(dashboard.isPublic())
                .isSystem(dashboard.isSystem())
                .systemDashboardType(dashboard.getSystemDashboardType())
                .build();
    }
}
