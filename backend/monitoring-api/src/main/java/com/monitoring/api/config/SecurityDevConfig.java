package com.monitoring.api.config;

import com.monitoring.auth.jwt.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Конфигурация Spring Security для режима разработки.
 * Эндпоинты приложений требуют JWT, остальное доступно публично для удобства разработки.
 */
@Configuration
@EnableWebSecurity
@Profile("dev")
@Order(1)
@RequiredArgsConstructor
public class SecurityDevConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {

        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/h2-console/**").permitAll()
                // Swagger UI / OpenAPI
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Ingestion аутентифицируется по X-API-Key в цепочке IngestSecurityConfig.
                .requestMatchers("/api/v1/ingest/**", "/api/ingest/**").permitAll()
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
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
