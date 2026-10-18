package com.monitoring.auth.organization.dto;

import com.monitoring.auth.organization.entity.MembershipRole;

public record MemberResponse(
        Long membershipId,
        Long userId,
        String username,
        String email,
        MembershipRole role) {
}
