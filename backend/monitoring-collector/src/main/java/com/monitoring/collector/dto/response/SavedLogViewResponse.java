package com.monitoring.collector.dto.response;

import com.monitoring.collector.dto.request.SavedLogViewQuery;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedLogViewResponse {

    private Long id;
    private String name;
    private SavedLogViewQuery queryParams;
    private Instant createdAt;
    private Instant updatedAt;
}
