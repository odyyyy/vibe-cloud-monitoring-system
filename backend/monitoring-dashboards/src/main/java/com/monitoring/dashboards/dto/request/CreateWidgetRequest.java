package com.monitoring.dashboards.dto.request;

import com.monitoring.dashboards.entity.WidgetType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payload for creating a widget. Layout values refer to a 12-column grid; the bounds are
 * part of the contract and are validated at the API boundary.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateWidgetRequest {

    public static final int GRID_COLUMNS = 12;
    public static final int MAX_CONFIG_LENGTH = 8000;
    public static final int MAX_HEIGHT = 24;
    public static final int MAX_ROW = 1000;

    @NotNull(message = "type is required")
    private WidgetType type;

    @NotBlank(message = "config is required")
    @Size(max = MAX_CONFIG_LENGTH, message = "config must be at most " + MAX_CONFIG_LENGTH + " characters")
    private String config;

    @Min(value = 0, message = "positionX must be greater than or equal to 0")
    @Max(value = GRID_COLUMNS - 1, message = "positionX must be less than " + GRID_COLUMNS)
    private int positionX;

    @Min(value = 0, message = "positionY must be greater than or equal to 0")
    @Max(value = MAX_ROW, message = "positionY is too large")
    private int positionY;

    @Min(value = 1, message = "width must be at least 1")
    @Max(value = GRID_COLUMNS, message = "width must be at most " + GRID_COLUMNS)
    private int width;

    @Min(value = 1, message = "height must be at least 1")
    @Max(value = MAX_HEIGHT, message = "height must be at most " + MAX_HEIGHT)
    private int height;
}
