package com.monitoring.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method whose successful completion must be recorded in the audit trail.
 *
 * <p>The interception is performed by {@code AuditLogAspect}. Only normal returns are
 * recorded; a method that throws does not produce an audit event (the action did not happen).
 *
 * @see com.monitoring.audit.aspect.AuditLogAspect
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditLog {

    /** Stable, machine-readable action code, for example {@code DASHBOARD_DELETED}. */
    String action();

    /**
     * Optional SpEL expression evaluated against the method arguments to derive the resource
     * identifier, for example {@code "#id"}. When blank, the first {@link Long} argument is
     * used if present.
     */
    String resourceId() default "";
}
