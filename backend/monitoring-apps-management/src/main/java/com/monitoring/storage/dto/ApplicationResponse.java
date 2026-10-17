package com.monitoring.storage.dto;

import com.monitoring.storage.entity.Application;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Public representation of an application.
 *
 * <p>Deliberately does not carry the API key: only its hash is stored, and the raw key is
 * returned once via {@link ApplicationWithKeyResponse} when it is created or regenerated.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationResponse {

    private Long id;
    private String name;
    private Instant createdAt;

    public static ApplicationResponse fromEntity(Application application) {
        return ApplicationResponse.builder()
                .id(application.getId())
                .name(application.getName())
                .createdAt(application.getCreatedAt())
                .build();
    }
}
