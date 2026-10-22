package com.monitoring.statuspage.dto.response;

import com.monitoring.statuspage.entity.StatusPageComponent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusPageComponentResponse {

    private Long id;
    private String name;
    private int sortOrder;
    private Long linkedHealthCheckId;

    public static StatusPageComponentResponse fromEntity(StatusPageComponent component) {
        return StatusPageComponentResponse.builder()
                .id(component.getId())
                .name(component.getName())
                .sortOrder(component.getSortOrder())
                .linkedHealthCheckId(component.getLinkedHealthCheckId())
                .build();
    }
}
