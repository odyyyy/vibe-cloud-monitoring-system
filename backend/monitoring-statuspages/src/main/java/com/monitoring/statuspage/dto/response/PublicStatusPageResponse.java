package com.monitoring.statuspage.dto.response;

import com.monitoring.statuspage.entity.ComponentStatus;
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
public class PublicStatusPageResponse {

    private String title;
    private String description;
    private ComponentStatus overallStatus;
    private List<PublicStatusPageComponentResponse> components;
    private Instant updatedAt;
    private List<PublicStatusPageOutageResponse> recentOutages;
}
