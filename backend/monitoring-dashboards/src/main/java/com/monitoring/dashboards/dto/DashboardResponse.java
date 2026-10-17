package com.monitoring.dashboards.dto;

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
    private Long userId;
    private Instant createdAt;
    private List<WidgetResponse> widgets;

    public static DashboardResponse fromEntity(Dashboard dashboard) {
        return DashboardResponse.builder()
                .id(dashboard.getId())
                .name(dashboard.getName())
                .userId(dashboard.getOwnerId())
                .createdAt(dashboard.getCreatedAt())
                .widgets(dashboard.getWidgets().stream().map(WidgetResponse::fromEntity).toList())
                .build();
    }
}
