package com.monitoring.statuspage.dto.response;

import com.monitoring.statuspage.entity.ComponentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicStatusPageComponentResponse {

    private String name;
    private ComponentStatus status;
}
