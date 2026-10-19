package com.monitoring.collector.controller;

import com.monitoring.collector.dto.response.CurrentMonthUsageResponse;
import com.monitoring.collector.dto.response.DailyUsagePointResponse;
import com.monitoring.collector.service.UsageQueryService;
import com.monitoring.organization.tenant.CurrentTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Usage and quota endpoints for the current organization.
 *
 * <p>The organization scope is derived from the authenticated tenant (the active
 * {@code X-Organization-Id} membership), never from a client-supplied parameter, so one
 * organization can never read another's usage.
 */
@RestController
@RequestMapping("/api/v1/usage")
@RequiredArgsConstructor
public class UsageController {

    private static final int DEFAULT_DAYS = 30;

    private final UsageQueryService usageQueryService;
    private final CurrentTenantService currentTenantService;

    /** Current calendar-month (UTC) totals and plan limits for the active organization. */
    @GetMapping("/current-month")
    public ResponseEntity<CurrentMonthUsageResponse> currentMonth() {
        Long organizationId = currentTenantService.requireOrganizationId();
        return ResponseEntity.ok(usageQueryService.currentMonth(organizationId, YearMonth.now(ZoneOffset.UTC)));
    }

    /** Daily totals for the last {@code days} days (1..90, default 30), oldest first. */
    @GetMapping("/daily")
    public ResponseEntity<List<DailyUsagePointResponse>> daily(
            @RequestParam(name = "days", defaultValue = "" + DEFAULT_DAYS) int days) {
        Long organizationId = currentTenantService.requireOrganizationId();
        return ResponseEntity.ok(
                usageQueryService.daily(organizationId, LocalDate.now(ZoneOffset.UTC), days));
    }
}
