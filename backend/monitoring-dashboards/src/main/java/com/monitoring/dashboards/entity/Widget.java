package com.monitoring.dashboards.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A widget placed on a dashboard.
 *
 * <p>{@code config} is a JSON string whose shape depends on {@code type}. It is validated
 * (valid JSON object) on the backend but its semantics are owned by the frontend renderers.
 * It must never contain executable expressions: the backend does not interpret it.
 *
 * <p>{@code positionX}/{@code positionY}/{@code width}/{@code height} describe the grid
 * layout cell in a 12-column grid.
 */
@Entity
@Table(
        name = "dashboard_widgets",
        indexes = @Index(name = "idx_dashboard_widgets_dashboard_id", columnList = "dashboard_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Widget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dashboard_id", nullable = false)
    private Dashboard dashboard;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WidgetType type;

    @Column(name = "config", nullable = false, columnDefinition = "TEXT")
    private String config;

    @Column(name = "position_x", nullable = false)
    private int positionX;

    @Column(name = "position_y", nullable = false)
    private int positionY;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;
}
