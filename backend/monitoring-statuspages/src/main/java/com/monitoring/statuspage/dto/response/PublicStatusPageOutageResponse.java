package com.monitoring.statuspage.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicStatusPageOutageResponse {

    private String componentName;
    private String title;
    private Instant startedAt;
    private Instant resolvedAt;
}
