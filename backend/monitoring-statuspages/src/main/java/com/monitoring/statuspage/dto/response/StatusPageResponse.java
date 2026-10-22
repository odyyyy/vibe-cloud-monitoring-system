package com.monitoring.statuspage.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.statuspage.entity.StatusPage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusPageResponse {

    private Long id;
    private Long organizationId;
    private String slug;
    private String title;
    private String description;
    @JsonProperty("isActive")
    private boolean active;
    private List<StatusPageComponentResponse> components;
    private Instant createdAt;
    private Instant updatedAt;

    public static StatusPageResponse fromEntity(StatusPage page) {
        return StatusPageResponse.builder()
                .id(page.getId())
                .organizationId(page.getOrganizationId())
                .slug(page.getSlug())
                .title(page.getTitle())
                .description(page.getDescription())
                .active(page.isActive())
                .components(page.getComponents().stream().map(StatusPageComponentResponse::fromEntity).toList())
                .createdAt(page.getCreatedAt())
                .updatedAt(page.getUpdatedAt())
                .build();
    }
}
