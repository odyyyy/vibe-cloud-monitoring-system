package com.monitoring.collector.entity;

import com.monitoring.apps.entity.Application;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A tenant-owned log processing rule evaluated in ascending {@code orderIndex}.
 *
 * <p>{@code application} is null for an organization-wide rule. Rules are applied by the Go
 * agent before logs leave the customer infrastructure and again by the ingestion service as a
 * fallback for direct API senders.
 */
@Entity
@Table(
        name = "log_pipeline_rules",
        indexes = {
                @Index(
                        name = "idx_log_pipeline_rules_org_order",
                        columnList = "organization_id, order_index"),
                @Index(
                        name = "idx_log_pipeline_rules_application_id",
                        columnList = "application_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogPipelineRule {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_FILTER_LENGTH = 1024;
    public static final int MAX_RULES_PER_ORGANIZATION = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "filter_expression", nullable = false, length = MAX_FILTER_LENGTH)
    private String filterExpression;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private LogPipelineAction action;

    /** JSON document; its shape is defined by {@link LogPipelineAction}. */
    @Column(name = "action_config", columnDefinition = "TEXT")
    private String actionConfig;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
