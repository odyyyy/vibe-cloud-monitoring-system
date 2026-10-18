package com.monitoring.auth.organization.dto;

import com.monitoring.auth.organization.entity.MembershipRole;

public record OrganizationResponse(Long id, String name, MembershipRole role) {
}
