package com.monitoring.auth.config;

import com.monitoring.auth.jwt.JwtAuthFilter;
import com.monitoring.auth.jwt.PatAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * The single catch-all Spring Security chain for the whole application (deny by default).
 *
 * <p>Separate chains exist only where a different authentication mechanism is required:
 * {@code IngestSecurityConfig} (@Order(0)) authenticates ingestion traffic via {@code X-API-Key}.
 * All other public paths are listed explicitly in {@code endpoints}; any path that is not
 * listed there is denied by default.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableAsync
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final PatAuthFilter patAuthFilter;

    /** The H2 console servlet is only registered when this property is true (dev-only). */
    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        List<String> endpoints = new ArrayList<>(List.of(
            "/api/auth/**",
            "/api/hello",
            // Liveness and readiness probes (see management.endpoint.health.group.*).
            "/actuator/health",
            "/actuator/health/**",
            // Prometheus scrape endpoint. It contains only platform telemetry (no tenant data)
            // and is intentionally not proxied by the frontend nginx container.
            "/actuator/prometheus",
            // Swagger UI / OpenAPI
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            // Spring's internal error dispatch (otherwise an unhandled error becomes 403).
            "/error",
            // Ingestion does not require JWT: agents authenticate with X-API-Key in a
            // dedicated chain (IngestSecurityConfig in monitoring-collector).
            "/api/v1/ingest/**",
            "/api/ingest/**",
            // Public share endpoint for dashboards (no authentication required)
            "/api/v1/dashboards/share/**",
            // Public status pages (no authentication required)
            "/api/v1/public/status-pages/**"
        ));
        if (h2ConsoleEnabled) {
            // The H2 web console is reachable only when explicitly enabled (dev-only).
            endpoints.add("/h2-console/**");
        }

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(endpoints.toArray(String[]::new)).permitAll()
                // Self-service profile changes (username/email) require an authenticated user;
                // the account is taken from the JWT principal inside ProfileController.
                .requestMatchers("/api/profile/**").authenticated()
                // Organization and membership management. The organization in the path is
                // authorized against the caller's membership inside OrganizationService.
                .requestMatchers("/api/v1/organizations/**").authenticated()
                .requestMatchers("/api/applications/**").authenticated()
                // Metrics queries require an authenticated user; ownership of the
                // requested application is verified in MetricQueryService.
                .requestMatchers("/api/v1/metrics/**").authenticated()
                // Log queries require an authenticated user; ownership of the requested
                // application is verified in LogQueryService.
                .requestMatchers("/api/v1/logs/**").authenticated()
                // Trace queries require an authenticated user; ownership of the requested
                // application is verified in TraceQueryService.
                .requestMatchers("/api/v1/traces/**").authenticated()
                // Dashboards and widgets require an authenticated user; ownership is
                // verified in DashboardService.
                .requestMatchers("/api/v1/dashboards/**").authenticated()
                // Platform administration requires an authenticated human session. The
                // PLATFORM_ADMIN role itself is enforced with method security on the
                // controller, so a normal authenticated account receives 403.
                .requestMatchers("/api/v1/admin/**").authenticated()
                // Saved log views require an authenticated user; they are scoped to the
                // owner in SavedLogViewService.
                .requestMatchers("/api/v1/saved-log-views/**").authenticated()
                // Health checks require an authenticated user; ownership is verified
                // in HealthCheckService.
                .requestMatchers("/api/v1/health-checks/**").authenticated()
                // Alert rules, notification channels and incident history require an
                // authenticated user; ownership is verified in AlertService.
                .requestMatchers("/api/v1/alert-rules/**", "/api/v1/notification-channels/**",
                        "/api/v1/alert-incidents/**", "/api/v1/incidents/**").authenticated()
                // Maintenance windows mute alerts for a tenant; ownership is verified in
                // MaintenanceWindowService.
                .requestMatchers("/api/v1/maintenance-windows/**").authenticated()
                // The audit trail is read-only and scoped to the authenticated user in
                // AuditEventService; there is no write/delete endpoint.
                .requestMatchers("/api/v1/audit-events/**").authenticated()
                // Workspace summary (Overview page) requires an authenticated user; the
                // counters are computed only across the user's own applications.
                .requestMatchers("/api/v1/overview", "/api/v1/overview/**").authenticated()
                // Usage and quota visualization requires an authenticated user; the scope is
                // taken from the active organization in UsageController.
                .requestMatchers("/api/v1/usage", "/api/v1/usage/**").authenticated()
                // Personal Access Tokens management requires authentication (JWT human users only);
                // PATs are denied from settings endpoints by the auth filter skipping settings paths.
                .requestMatchers("/api/v1/settings/pats", "/api/v1/settings/pats/**").authenticated()
                // Deny by default: any endpoint not explicitly listed above is not reachable.
                // Public endpoints must be added to `endpoints` deliberately.
                .anyRequest().denyAll()
            )
            // Requests without a valid bearer token receive 401 instead of 403, so clients can
            // distinguish "not authenticated" from "authenticated but forbidden".
            .exceptionHandling(ex -> ex.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(patAuthFilter, JwtAuthFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
