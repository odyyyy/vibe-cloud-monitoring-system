package com.monitoring.api.exception;

import com.monitoring.auth.organization.exception.InviteTargetNotFoundException;
import com.monitoring.auth.organization.exception.LastOwnerException;
import com.monitoring.auth.organization.exception.MemberAlreadyExistsException;
import com.monitoring.auth.organization.exception.MemberNotFoundException;
import com.monitoring.auth.organization.exception.OrganizationNotFoundException;
import com.monitoring.auth.organization.exception.TenantAccessDeniedException;
import com.monitoring.collector.exception.SavedLogViewAccessDeniedException;
import com.monitoring.collector.exception.SavedLogViewLimitExceededException;
import com.monitoring.collector.exception.SavedLogViewNotFoundException;
import com.monitoring.collector.exception.InvalidTraceException;
import com.monitoring.collector.exception.TraceNotFoundException;
import com.monitoring.dashboards.exception.DashboardAccessDeniedException;
import com.monitoring.dashboards.exception.DashboardNotFoundException;
import com.monitoring.dashboards.exception.InvalidWidgetConfigException;
import com.monitoring.dashboards.exception.WidgetLimitExceededException;
import com.monitoring.dashboards.exception.WidgetNotFoundException;
import com.monitoring.alerts.exception.AlertRuleAccessDeniedException;
import com.monitoring.alerts.exception.AlertRuleNotFoundException;
import com.monitoring.alerts.exception.InvalidAlertRuleException;
import com.monitoring.alerts.exception.InvalidWebhookUrlException;
import com.monitoring.alerts.exception.NotificationChannelAccessDeniedException;
import com.monitoring.alerts.exception.NotificationChannelNotFoundException;
import com.monitoring.healthcheck.exception.HealthCheckAccessDeniedException;
import com.monitoring.healthcheck.exception.HealthCheckNotFoundException;
import com.monitoring.healthcheck.exception.InvalidTargetUrlException;
import com.monitoring.storage.exception.ApplicationAccessDeniedException;
import com.monitoring.storage.exception.ApplicationNameConflictException;
import com.monitoring.storage.exception.ApplicationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApplicationNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ApplicationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    @ExceptionHandler(TenantAccessDeniedException.class)
    public ResponseEntity<ApiError> handleTenantAccessDenied(TenantAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(HttpStatus.FORBIDDEN.value(), ex.getMessage()));
    }

    @ExceptionHandler({OrganizationNotFoundException.class, MemberNotFoundException.class,
            InviteTargetNotFoundException.class})
    public ResponseEntity<ApiError> handleOrganizationNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    @ExceptionHandler({MemberAlreadyExistsException.class, LastOwnerException.class})
    public ResponseEntity<ApiError> handleOrganizationConflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    @ExceptionHandler(ApplicationAccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(ApplicationAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(HttpStatus.FORBIDDEN.value(), ex.getMessage()));
    }

    @ExceptionHandler({DashboardNotFoundException.class, WidgetNotFoundException.class})
    public ResponseEntity<ApiError> handleDashboardNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    @ExceptionHandler(DashboardAccessDeniedException.class)
    public ResponseEntity<ApiError> handleDashboardAccessDenied(DashboardAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(HttpStatus.FORBIDDEN.value(), ex.getMessage()));
    }

    @ExceptionHandler(InvalidWidgetConfigException.class)
    public ResponseEntity<ApiError> handleInvalidWidgetConfig(InvalidWidgetConfigException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    @ExceptionHandler(HealthCheckNotFoundException.class)
    public ResponseEntity<ApiError> handleHealthCheckNotFound(HealthCheckNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    @ExceptionHandler(HealthCheckAccessDeniedException.class)
    public ResponseEntity<ApiError> handleHealthCheckAccessDenied(HealthCheckAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(HttpStatus.FORBIDDEN.value(), ex.getMessage()));
    }

    /** Blocked target URLs (wrong scheme, credentials, or non-public address). */
    @ExceptionHandler(InvalidTargetUrlException.class)
    public ResponseEntity<ApiError> handleInvalidTargetUrl(InvalidTargetUrlException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    @ExceptionHandler({AlertRuleNotFoundException.class, NotificationChannelNotFoundException.class})
    public ResponseEntity<ApiError> handleAlertNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    @ExceptionHandler({AlertRuleAccessDeniedException.class, NotificationChannelAccessDeniedException.class})
    public ResponseEntity<ApiError> handleAlertAccessDenied(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(HttpStatus.FORBIDDEN.value(), ex.getMessage()));
    }

    /** Inconsistent alert rule payloads and blocked webhook URLs. */
    @ExceptionHandler({InvalidAlertRuleException.class, InvalidWebhookUrlException.class})
    public ResponseEntity<ApiError> handleInvalidAlertRequest(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    /**
     * Name conflicts and quota limits. Only typed exceptions with server-authored messages are
     * mapped here; unexpected exceptions fall through to the default handler (HTTP 500 without
     * internal details).
     */
    @ExceptionHandler({ApplicationNameConflictException.class, WidgetLimitExceededException.class,
            SavedLogViewLimitExceededException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    @ExceptionHandler(SavedLogViewNotFoundException.class)
    public ResponseEntity<ApiError> handleSavedLogViewNotFound(SavedLogViewNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    @ExceptionHandler(SavedLogViewAccessDeniedException.class)
    public ResponseEntity<ApiError> handleSavedLogViewAccessDenied(SavedLogViewAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(HttpStatus.FORBIDDEN.value(), ex.getMessage()));
    }

    @ExceptionHandler(TraceNotFoundException.class)
    public ResponseEntity<ApiError> handleTraceNotFound(TraceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    /** Trace-level ingestion constraints (for example too many spans for a single trace). */
    @ExceptionHandler(InvalidTraceException.class)
    public ResponseEntity<ApiError> handleInvalidTrace(InvalidTraceException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), "Validation failed", fieldErrors));
    }

    /** Malformed JSON bodies, unknown enum values, unparseable timestamps, etc. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), "Malformed request body"));
    }

    /** Invalid query parameter types (for example an unknown log level). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), "Invalid value for parameter '" + ex.getName() + "'"));
    }
}
