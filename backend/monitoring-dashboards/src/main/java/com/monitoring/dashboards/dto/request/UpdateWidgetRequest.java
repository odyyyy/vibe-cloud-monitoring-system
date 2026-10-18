package com.monitoring.dashboards.dto.request;

import com.monitoring.dashboards.entity.WidgetType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Partial update of a widget. Every field is optional; only provided fields are applied.
 * Bounds and sizes mirror {@link CreateWidgetRequest}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateWidgetRequest {

    private WidgetType type;

    @Size(max = CreateWidgetRequest.MAX_CONFIG_LENGTH,
            message = "config must be at most " + CreateWidgetRequest.MAX_CONFIG_LENGTH + " characters")
    private String config;

    @Min(value = 0, message = "positionX must be greater than or equal to 0")
    @Max(value = CreateWidgetRequest.GRID_COLUMNS - 1,
            message = "positionX must be less than " + CreateWidgetRequest.GRID_COLUMNS)
    private Integer positionX;

    @Min(value = 0, message = "positionY must be greater than or equal to 0")
    @Max(value = CreateWidgetRequest.MAX_ROW, message = "positionY is too large")
    private Integer positionY;

    @Min(value = 1, message = "width must be at least 1")
    @Max(value = CreateWidgetRequest.GRID_COLUMNS,
            message = "width must be at most " + CreateWidgetRequest.GRID_COLUMNS)
    private Integer width;

    @Min(value = 1, message = "height must be at least 1")
    @Max(value = CreateWidgetRequest.MAX_HEIGHT,
            message = "height must be at most " + CreateWidgetRequest.MAX_HEIGHT)
    private Integer height;
}
