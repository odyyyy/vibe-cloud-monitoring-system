package com.monitoring.organization.dto.response;

import com.monitoring.organization.entity.MembershipRole;

public record MemberResponse(
        Long membershipId,
        Long userId,
        String username,
        String email,
        MembershipRole role) {
}
