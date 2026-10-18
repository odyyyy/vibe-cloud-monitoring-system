package com.monitoring.organization.tenant;

import com.monitoring.organization.entity.MembershipRole;

/**
 * The organization the current request is acting in, together with the caller's role.
 */
public record TenantContext(Long userId, Long organizationId, MembershipRole role) {
}
