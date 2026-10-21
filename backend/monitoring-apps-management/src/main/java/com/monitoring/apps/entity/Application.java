package com.monitoring.apps.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * SHA-256 hash of the agent API key, never the plaintext value.
     * The raw key is generated and shown to the owner exactly once.
     */
    @Column(name = "api_key_hash", nullable = false, unique = true, length = 64)
    private String apiKeyHash;

    /**
     * Short, non-sensitive display hint such as {@code app_…1f2e}. It contains only the
     * public prefix and the last four characters of the key, so the owner can identify
     * which key is configured without revealing the full credential.
     */
    @Column(name = "api_key_hint", length = 32)
    private String apiKeyHint;

    @Column(nullable = false)
    private Long organizationId;

    /**
     * Marks the internal application that receives the platform's own metrics.
     * System applications are readable by every authenticated tenant but own no customer data.
     */
    @Column(name = "is_system", nullable = false)
    private boolean isSystem;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
