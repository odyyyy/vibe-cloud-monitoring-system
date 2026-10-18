package com.monitoring.organization.dto.response;

import com.monitoring.organization.entity.MembershipRole;

public record OrganizationResponse(Long id, String name, MembershipRole role) {
}
