package com.monitoring.apps.dto.response;

import com.monitoring.apps.entity.Application;
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
 * {@code apiKeyHint} is a short non-sensitive mask (for example {@code app_…1f2e}) used by
 * the UI to help the owner identify the active key.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationResponse {

    private Long id;
    private String name;
    private String apiKeyHint;
    private Instant createdAt;

    public static ApplicationResponse fromEntity(Application application) {
        return ApplicationResponse.builder()
                .id(application.getId())
                .name(application.getName())
                .apiKeyHint(application.getApiKeyHint())
                .createdAt(application.getCreatedAt())
                .build();
    }
}
