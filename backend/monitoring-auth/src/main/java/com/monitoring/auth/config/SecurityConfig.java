package com.monitoring.auth.config;

import com.monitoring.auth.jwt.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Единственная catch-all цепочка Spring Security для всего приложения (deny-by-default).
 *
 * <p>Отдельные цепочки существуют только там, где нужен другой механизм аутентификации:
 * {@code IngestSecurityConfig} (@Order(0)) для ingestion по {@code X-API-Key}. Все остальные
 * публичные пути перечислены явно в {@code endpoints}; незаявленный путь по умолчанию запрещён.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        String[] endpoints = {
            "/api/auth/**",
            "/api/hello",
            "/actuator/health",
            "/actuator/info",
            // Swagger UI / OpenAPI
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            // Внутренняя обработка ошибок Spring (иначе необработанная ошибка станет 403).
            "/error",
            // Веб-консоль H2 доступна только когда она включена (spring.h2.console.enabled),
            // иначе сервлет не зарегистрирован и путь возвращает 404.
            "/h2-console/**",
            // Ingestion не требует JWT: агенты аутентифицируются по X-API-Key
            // в отдельной цепочке (IngestSecurityConfig в monitoring-collector).
            "/api/v1/ingest/**",
            "/api/ingest/**"
        };
        
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(endpoints).permitAll()
                .requestMatchers("/api/applications/**").authenticated()
                // Metrics queries require an authenticated user; ownership of the
                // requested application is verified in MetricQueryService.
                .requestMatchers("/api/v1/metrics/**").authenticated()
                // Log queries require an authenticated user; ownership of the requested
                // application is verified in LogQueryService.
                .requestMatchers("/api/v1/logs/**").authenticated()
                // Dashboards and widgets require an authenticated user; ownership is
                // verified in DashboardService.
                .requestMatchers("/api/v1/dashboards/**").authenticated()
                // Health checks require an authenticated user; ownership is verified
                // in HealthCheckService.
                .requestMatchers("/api/v1/health-checks/**").authenticated()
                // Deny by default: any endpoint not explicitly listed above is not reachable.
                // Public endpoints must be added to `endpoints` deliberately.
                .anyRequest().denyAll()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}