package com.monitoring.auth.organization.dto.request;

import com.monitoring.auth.organization.entity.MembershipRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
        @NotNull(message = "Role is required")
        MembershipRole role) {
}
