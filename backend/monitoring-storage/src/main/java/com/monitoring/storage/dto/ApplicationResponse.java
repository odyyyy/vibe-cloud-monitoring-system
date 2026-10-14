package com.monitoring.storage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationResponse {

    private Long id;
    private String name;
    private String apiKey;
    private LocalDateTime createdAt;

    public static ApplicationResponse fromEntity(Application application) {
        return ApplicationResponse.builder()
                .id(application.getId())
                .name(application.getName())
                .apiKey(application.getApiKey())
                .createdAt(application.getCreatedAt())
                .build();
    }
}
