package com.monitoring.organization.dto.request;

import com.monitoring.organization.entity.MembershipRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
        @NotNull(message = "Role is required")
        MembershipRole role) {
}
