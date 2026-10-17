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
 * Dedicated security chain for the ingestion endpoints.
 *
 * <p>It matches only {@code /api/v1/ingest/**} (and the {@code /api/ingest/**} alias) and has
 * {@link Order @Order(0)}, so it is evaluated before the catch-all chains. Inside the chain the
 * request is authenticated by {@link ApiKeyAuthFilter} (HTTP 401 for a missing or invalid key);
 * JWT is not used here.
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
