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

    public static DashboardResponse fromEntity(Dashboard dashboard, List<DashboardVariable> variables) {
        return DashboardResponse.builder()
                .id(dashboard.getId())
                .name(dashboard.getName())
                .organizationId(dashboard.getOrganizationId())
                .createdAt(dashboard.getCreatedAt())
                .variables(variables)
                .widgets(dashboard.getWidgets().stream().map(WidgetResponse::fromEntity).toList())
                .build();
    }
}
