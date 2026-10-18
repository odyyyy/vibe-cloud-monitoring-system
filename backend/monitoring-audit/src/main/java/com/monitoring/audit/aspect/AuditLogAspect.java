package com.monitoring.audit.aspect;

import com.monitoring.audit.annotation.AuditLog;
import com.monitoring.audit.service.AuditEventService;
import com.monitoring.audit.service.ClientIpResolver;
import com.monitoring.auth.organization.exception.TenantAccessDeniedException;
import com.monitoring.auth.organization.tenant.CurrentTenantService;
import com.monitoring.auth.organization.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * Records an {@link AuditLog} for every successfully completed, annotated controller method.
 *
 * <p>The event is written synchronously on the request thread, after the intercepted method
 * has returned (so business work — including its transaction — is already committed). A
 * failure to persist the event is logged at error level but does not fail the request: the
 * action has already happened and cannot be rolled back, so turning it into an error response
 * would mislead the client. The trail remains durable whenever the database is available.
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();
    private static final int MAX_RESOURCE_ID_LENGTH = 128;

    private final AuditEventService auditEventService;
    private final CurrentTenantService currentTenantService;

    public AuditLogAspect(AuditEventService auditEventService, CurrentTenantService currentTenantService) {
        this.auditEventService = auditEventService;
        this.currentTenantService = currentTenantService;
    }

    @AfterReturning(pointcut = "@annotation(auditLog)", returning = "result")
    public void record(JoinPoint joinPoint, AuditLog auditLog, Object result) {
        TenantContext tenant;
        try {
            tenant = currentTenantService.requireTenant();
        } catch (TenantAccessDeniedException ex) {
            log.warn("Audit action '{}' completed without a tenant context; event not recorded",
                    auditLog.action());
            return;
        }

        String resourceId = resolveResourceId(joinPoint, auditLog);
        String ipAddress = ClientIpResolver.resolve(currentRequest());

        try {
            auditEventService.record(
                    tenant.organizationId(), tenant.userId(), auditLog.action(), resourceId, ipAddress);
        } catch (RuntimeException ex) {
            log.error("Failed to persist audit event action='{}' userId={}",
                    auditLog.action(), tenant.userId(), ex);
        }
    }

    private String resolveResourceId(JoinPoint joinPoint, AuditLog auditLog) {
        String expression = auditLog.resourceId();
        if (expression != null && !expression.isBlank()) {
            try {
                MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                Method method = signature.getMethod();
                MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                        joinPoint.getTarget(), method, joinPoint.getArgs(), PARAMETER_NAME_DISCOVERER);
                Object value = EXPRESSION_PARSER.parseExpression(expression).getValue(context);
                if (value != null) {
                    return truncate(String.valueOf(value));
                }
            } catch (RuntimeException ex) {
                log.warn("Could not evaluate audit resourceId expression '{}'", expression, ex);
            }
        }

        for (Object argument : joinPoint.getArgs()) {
            if (argument instanceof Long id) {
                return String.valueOf(id);
            }
        }
        return null;
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes
                ? servletAttributes.getRequest()
                : null;
    }

    private static String truncate(String value) {
        return value.length() <= MAX_RESOURCE_ID_LENGTH ? value : value.substring(0, MAX_RESOURCE_ID_LENGTH);
    }
}
