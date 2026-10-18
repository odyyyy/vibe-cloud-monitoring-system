package com.monitoring.auth.organization.security;

import com.monitoring.auth.organization.entity.MembershipRole;
import com.monitoring.auth.organization.exception.TenantAccessDeniedException;
import com.monitoring.auth.organization.tenant.CurrentTenantService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Enforces {@link RequireRole} using the caller's membership in the active organization.
 *
 * <p>The active organization is resolved from the request (see {@link CurrentTenantService}),
 * so a user cannot gain a role by supplying another organization: membership is checked for
 * that exact organization.
 */
@Aspect
@Component
public class RequireRoleAspect {

    private final CurrentTenantService currentTenantService;

    public RequireRoleAspect(CurrentTenantService currentTenantService) {
        this.currentTenantService = currentTenantService;
    }

    @Before("@annotation(requireRole)")
    public void check(JoinPoint joinPoint, RequireRole requireRole) {
        MembershipRole actual = currentTenantService.requireTenant().role();
        boolean allowed = Arrays.asList(requireRole.value()).contains(actual);
        if (!allowed) {
            throw new TenantAccessDeniedException("Your role does not permit this action");
        }
    }
}
