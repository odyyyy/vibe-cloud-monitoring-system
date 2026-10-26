package com.monitoring.api.exception;

import com.monitoring.common.error.ApiError;
import com.monitoring.organization.exception.InviteTargetNotFoundException;
import com.monitoring.organization.exception.LastOwnerException;
import com.monitoring.organization.exception.MemberAlreadyExistsException;
import com.monitoring.organization.exception.MemberNotFoundException;
import com.monitoring.organization.exception.OrganizationNotFoundException;
import com.monitoring.organization.exception.TenantAccessDeniedException;
import com.monitoring.collector.exception.SavedLogViewAccessDeniedException;
import com.monitoring.collector.exception.SavedLogViewLimitExceededException;
import com.monitoring.collector.exception.SavedLogViewNotFoundException;
import com.monitoring.collector.exception.InvalidExportFormatException;
import com.monitoring.collector.exception.InvalidLogFacetRequestException;
import com.monitoring.collector.exception.LogFacetRateLimitedException;
import com.monitoring.collector.exception.LogFacetTimeoutException;
import com.monitoring.retention.exception.InvalidRetentionPolicyException;
import com.monitoring.collector.exception.InvalidTraceException;
import com.monitoring.collector.exception.TraceNotFoundException;
import com.monitoring.dashboards.exception.DashboardAccessDeniedException;
import com.monitoring.dashboards.exception.DashboardNotFoundException;
import com.monitoring.dashboards.exception.InvalidDashboardVariableException;
import com.monitoring.dashboards.exception.InvalidWidgetConfigException;
import com.monitoring.dashboards.exception.InvalidSharedQueryException;
import com.monitoring.dashboards.exception.SharedDashboardNotFoundException;
import com.monitoring.dashboards.exception.WidgetLimitExceededException;
import com.monitoring.dashboards.exception.WidgetNotFoundException;
import com.monitoring.alerts.exception.AlertIncidentAccessDeniedException;
import com.monitoring.alerts.exception.AlertIncidentNotFoundException;
import com.monitoring.alerts.exception.AlertPreviewRateLimitedException;
import com.monitoring.alerts.exception.AlertRuleAccessDeniedException;
import com.monitoring.alerts.exception.AlertRuleNotFoundException;
import com.monitoring.alerts.exception.IncidentCommentRateLimitedException;
import com.monitoring.alerts.exception.InvalidAlertRuleException;
import com.monitoring.alerts.exception.InvalidIncidentCommentException;
import com.monitoring.alerts.exception.InvalidIncidentNoteException;
import com.monitoring.alerts.exception.InvalidIncidentStateException;
import com.monitoring.alerts.exception.InvalidMaintenanceWindowException;
import com.monitoring.alerts.exception.InvalidNotificationChannelException;
import com.monitoring.alerts.exception.InvalidWebhookUrlException;
import com.monitoring.alerts.exception.LogAlertRuleLimitExceededException;
import com.monitoring.alerts.exception.MaintenanceWindowAccessDeniedException;
import com.monitoring.alerts.exception.MaintenanceWindowNotFoundException;
import com.monitoring.alerts.exception.NotificationChannelAccessDeniedException;
import com.monitoring.alerts.exception.NotificationChannelLimitExceededException;
import com.monitoring.alerts.exception.NotificationChannelNotFoundException;
import com.monitoring.alerts.exception.InvalidScheduledReportException;
import com.monitoring.alerts.exception.ScheduledReportAccessDeniedException;
import com.monitoring.alerts.exception.ScheduledReportLimitExceededException;
import com.monitoring.alerts.exception.ScheduledReportNotFoundException;
import com.monitoring.healthcheck.exception.HealthCheckAccessDeniedException;
import com.monitoring.healthcheck.exception.HealthCheckNotFoundException;
import com.monitoring.healthcheck.exception.InvalidTargetUrlException;
import com.monitoring.statuspage.exception.InvalidStatusPageException;
import com.monitoring.statuspage.exception.StatusPageAccessDeniedException;
import com.monitoring.statuspage.exception.StatusPageLimitExceededException;
import com.monitoring.statuspage.exception.StatusPageNotFoundException;
import com.monitoring.statuspage.exception.StatusPageRateLimitedException;
import com.monitoring.statuspage.exception.StatusPageSlugConflictException;
import com.monitoring.apps.exception.ApplicationAccessDeniedException;
import com.monitoring.apps.exception.ApplicationNameConflictException;
import com.monitoring.apps.exception.ApplicationNotFoundException;
import com.monitoring.revisions.exception.ConfigRevisionNotFoundException;
import com.monitoring.revisions.exception.ConfigRevisionRestoreConflictException;
import com.monitoring.revisions.exception.InvalidConfigSnapshotException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
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

    @ExceptionHandler({DashboardNotFoundException.class, WidgetNotFoundException.class,
            ConfigRevisionNotFoundException.class})
    public ResponseEntity<ApiError> handleDashboardNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    /** A snapshot cannot be restored because a referenced resource is missing or foreign. */
    @ExceptionHandler(ConfigRevisionRestoreConflictException.class)
    public ResponseEntity<ApiError> handleRevisionRestoreConflict(
            ConfigRevisionRestoreConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    /** A stored snapshot does not satisfy the current schema; the resource is left unchanged. */
    @ExceptionHandler(InvalidConfigSnapshotException.class)
    public ResponseEntity<ApiError> handleInvalidConfigSnapshot(InvalidConfigSnapshotException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError(HttpStatus.UNPROCESSABLE_ENTITY.value(), ex.getMessage()));
    }

    @ExceptionHandler(DashboardAccessDeniedException.class)
    public ResponseEntity<ApiError> handleDashboardAccessDenied(DashboardAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(HttpStatus.FORBIDDEN.value(), ex.getMessage()));
    }

    /**
     * Insufficient privileges, for example a missing platform role on an administration
     * endpoint. The message is written by the platform and never contains internals.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(HttpStatus.FORBIDDEN.value(), "You do not have permission to perform this action"));
    }

    /** Unknown, revoked or mismatched public share link. */
    @ExceptionHandler(SharedDashboardNotFoundException.class)
    public ResponseEntity<ApiError> handleSharedDashboardNotFound(SharedDashboardNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    /** Invalid time range or widget configuration in a public shared-dashboard query. */
    @ExceptionHandler(InvalidRetentionPolicyException.class)
    public ResponseEntity<ApiError> handleInvalidRetentionPolicy(InvalidRetentionPolicyException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    @ExceptionHandler(InvalidSharedQueryException.class)
    public ResponseEntity<ApiError> handleInvalidSharedQuery(InvalidSharedQueryException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    @ExceptionHandler(InvalidWidgetConfigException.class)
    public ResponseEntity<ApiError> handleInvalidWidgetConfig(InvalidWidgetConfigException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    @ExceptionHandler(InvalidDashboardVariableException.class)
    public ResponseEntity<ApiError> handleInvalidDashboardVariable(InvalidDashboardVariableException ex) {
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

    @ExceptionHandler({AlertRuleNotFoundException.class, NotificationChannelNotFoundException.class,
            AlertIncidentNotFoundException.class, MaintenanceWindowNotFoundException.class,
            ScheduledReportNotFoundException.class})
    public ResponseEntity<ApiError> handleAlertNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    @ExceptionHandler({AlertRuleAccessDeniedException.class, NotificationChannelAccessDeniedException.class,
            AlertIncidentAccessDeniedException.class, MaintenanceWindowAccessDeniedException.class,
            ScheduledReportAccessDeniedException.class})
    public ResponseEntity<ApiError> handleAlertAccessDenied(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(HttpStatus.FORBIDDEN.value(), ex.getMessage()));
    }

    /** Inconsistent alert rule payloads and blocked webhook/Slack/email destinations. */
    @ExceptionHandler({InvalidAlertRuleException.class, InvalidWebhookUrlException.class,
            InvalidNotificationChannelException.class, InvalidIncidentNoteException.class,
            InvalidIncidentCommentException.class, InvalidMaintenanceWindowException.class,
            InvalidScheduledReportException.class})
    public ResponseEntity<ApiError> handleInvalidAlertRequest(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    /** Invalid lifecycle transitions, for example resolving an already resolved incident. */
    @ExceptionHandler(InvalidIncidentStateException.class)
    public ResponseEntity<ApiError> handleInvalidIncidentState(InvalidIncidentStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    /**
     * Name conflicts and quota limits. Only typed exceptions with server-authored messages are
     * mapped here; unexpected exceptions fall through to the default handler (HTTP 500 without
     * internal details).
     */
    @ExceptionHandler({ApplicationNameConflictException.class, WidgetLimitExceededException.class,
            SavedLogViewLimitExceededException.class, LogAlertRuleLimitExceededException.class,
            NotificationChannelLimitExceededException.class,
            StatusPageLimitExceededException.class, StatusPageSlugConflictException.class,
            ScheduledReportLimitExceededException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    @ExceptionHandler(StatusPageNotFoundException.class)
    public ResponseEntity<ApiError> handleStatusPageNotFound(StatusPageNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    @ExceptionHandler(StatusPageAccessDeniedException.class)
    public ResponseEntity<ApiError> handleStatusPageAccessDenied(StatusPageAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(HttpStatus.FORBIDDEN.value(), ex.getMessage()));
    }

    @ExceptionHandler(InvalidStatusPageException.class)
    public ResponseEntity<ApiError> handleInvalidStatusPage(InvalidStatusPageException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    /** Public status page requests are throttled per client IP; honor the advertised retry delay. */
    @ExceptionHandler(StatusPageRateLimitedException.class)
    public ResponseEntity<ApiError> handleStatusPageRateLimited(StatusPageRateLimitedException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(ex.getRetryAfterSeconds()))
                .body(new ApiError(HttpStatus.TOO_MANY_REQUESTS.value(), ex.getMessage()));
    }

    /** The per-organization preview budget was exhausted; clients may retry after the delay. */
    @ExceptionHandler(AlertPreviewRateLimitedException.class)
    public ResponseEntity<ApiError> handlePreviewRateLimited(AlertPreviewRateLimitedException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(ex.getRetryAfterSeconds()))
                .body(new ApiError(HttpStatus.TOO_MANY_REQUESTS.value(), ex.getMessage()));
    }

    /** A user posted incident comments faster than the configured budget allows. */
    @ExceptionHandler(IncidentCommentRateLimitedException.class)
    public ResponseEntity<ApiError> handleIncidentCommentRateLimited(
            IncidentCommentRateLimitedException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(ex.getRetryAfterSeconds()))
                .body(new ApiError(HttpStatus.TOO_MANY_REQUESTS.value(), ex.getMessage()));
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

    /** Unknown {@code format} value on a log or metric query export request. */
    @ExceptionHandler(InvalidExportFormatException.class)
    public ResponseEntity<ApiError> handleInvalidExportFormat(InvalidExportFormatException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    /** Unknown facet field or an out-of-range facet limit. */
    @ExceptionHandler(InvalidLogFacetRequestException.class)
    public ResponseEntity<ApiError> handleInvalidLogFacetRequest(InvalidLogFacetRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    /** The per-organization facet budget was exhausted; clients may retry after the delay. */
    @ExceptionHandler(LogFacetRateLimitedException.class)
    public ResponseEntity<ApiError> handleLogFacetRateLimited(LogFacetRateLimitedException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(ex.getRetryAfterSeconds()))
                .body(new ApiError(HttpStatus.TOO_MANY_REQUESTS.value(), ex.getMessage()));
    }

    /**
     * The facet aggregation exceeded its statement timeout. The log result list is unaffected,
     * so the explorer keeps working without facets.
     */
    @ExceptionHandler(LogFacetTimeoutException.class)
    public ResponseEntity<ApiError> handleLogFacetTimeout(LogFacetTimeoutException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getMessage()));
    }

    /** Invalid global-search query, resource type or limit. */
    @ExceptionHandler(InvalidSearchRequestException.class)
    public ResponseEntity<ApiError> handleInvalidSearchRequest(InvalidSearchRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    /** The per-user search budget was exhausted; clients may retry after the delay. */
    @ExceptionHandler(SearchRateLimitedException.class)
    public ResponseEntity<ApiError> handleSearchRateLimited(SearchRateLimitedException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(ex.getRetryAfterSeconds()))
                .body(new ApiError(HttpStatus.TOO_MANY_REQUESTS.value(), ex.getMessage()));
    }

    /** A search query exceeded its statement timeout. */
    @ExceptionHandler(SearchTimeoutException.class)
    public ResponseEntity<ApiError> handleSearchTimeout(SearchTimeoutException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getMessage()));
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
