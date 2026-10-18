package com.monitoring.auth.organization.security;

import com.monitoring.auth.organization.entity.MembershipRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which roles (in the caller's active organization) may invoke a method.
 *
 * <pre>{@code @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})}</pre>
 *
 * <p>Enforcement happens in {@link RequireRoleAspect} before the annotated method runs.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    MembershipRole[] value();
}
