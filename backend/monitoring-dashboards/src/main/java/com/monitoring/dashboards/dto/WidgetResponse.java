package com.monitoring.dashboards.dto;

import com.monitoring.dashboards.entity.Widget;
import com.monitoring.dashboards.entity.WidgetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WidgetResponse {

    private Long id;
    private Long dashboardId;
    private WidgetType type;
    private String config;
    private int positionX;
    private int positionY;
    private int width;
    private int height;

    public static WidgetResponse fromEntity(Widget widget) {
        return WidgetResponse.builder()
                .id(widget.getId())
                .dashboardId(widget.getDashboard().getId())
                .type(widget.getType())
                .config(widget.getConfig())
                .positionX(widget.getPositionX())
                .positionY(widget.getPositionY())
                .width(widget.getWidth())
                .height(widget.getHeight())
                .build();
    }
}
