package com.monitoring.dashboards.dto.snapshot;

import com.monitoring.dashboards.dto.DashboardVariable;
import com.monitoring.dashboards.entity.WidgetType;

import java.util.List;

/**
 * JSON document stored in a dashboard revision.
 *
 * <p>Intentionally excludes identifiers, the tenant, timestamps and every sharing field, so a
 * restore can never re-enable or change public access.
 */
public record DashboardSnapshot(
        String name,
        List<DashboardVariable> variables,
        List<WidgetSnapshot> widgets
) {

    public record WidgetSnapshot(
            WidgetType type,
            String config,
            int positionX,
            int positionY,
            int width,
            int height
    ) {
    }
}
