package com.monitoring.auth.organization.dto.response;

import com.monitoring.auth.organization.entity.MembershipRole;

public record OrganizationResponse(Long id, String name, MembershipRole role) {
}
