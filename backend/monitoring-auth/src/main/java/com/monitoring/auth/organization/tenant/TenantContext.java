package com.monitoring.auth.organization.tenant;

import com.monitoring.auth.organization.entity.MembershipRole;

/**
 * The organization the current request is acting in, together with the caller's role.
 */
public record TenantContext(Long userId, Long organizationId, MembershipRole role) {
}
