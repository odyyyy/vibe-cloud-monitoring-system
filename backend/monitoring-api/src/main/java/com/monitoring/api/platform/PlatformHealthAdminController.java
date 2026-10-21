package com.monitoring.api.platform;

import com.monitoring.dashboards.dto.response.DashboardResponse;
import com.monitoring.dashboards.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-administration API. Access is restricted to accounts whose platform role is
 * {@code PLATFORM_ADMIN}; ordinary tenant roles ({@code OWNER}/{@code ADMIN}/{@code VIEWER})
 * are not sufficient.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class PlatformHealthAdminController {

    private final DashboardService dashboardService;

    @GetMapping("/platform-health")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<DashboardResponse> platformHealth() {
        return ResponseEntity.ok(
                dashboardService.getSystemDashboard(PlatformResourceService.PLATFORM_HEALTH_DASHBOARD_TYPE));
    }
}
