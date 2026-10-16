package com.monitoring.collector.security;

import com.monitoring.storage.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Отдельная security-цепочка для ingestion-эндпоинтов.
 *
 * <p>Матчится только по {@code /api/v1/ingest/**} (и алиасу {@code /api/ingest/**})
 * и имеет {@link Order @Order(0)}, поэтому проверяется раньше catch-all цепочек
 * в auth/dev-конфигурациях. Внутри цепочки запрос аутентифицируется
 * {@link ApiKeyAuthFilter} (HTTP 401 при отсутствующем/невалидном ключе), JWT не применяется.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Order(0)
public class IngestSecurityConfig {

    private final ApplicationRepository applicationRepository;

    @Bean
    @Order(0)
    public SecurityFilterChain ingestFilterChain(HttpSecurity http) throws Exception {
        ApiKeyAuthFilter apiKeyAuthFilter = new ApiKeyAuthFilter(applicationRepository);

        http
            .securityMatcher("/api/v1/ingest/**", "/api/ingest/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
