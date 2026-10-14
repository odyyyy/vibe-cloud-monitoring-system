package com.monitoring.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Конфигурация Spring Security для режима разработки.
 * Отключает CSRF и предоставляет публичный доступ для удобства разработки.
 */
@Configuration
@EnableWebSecurity
@Profile("dev")
public class SecurityDevConfig {

    @Bean
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/hello", "/api/auth/**", "/h2-console/**").permitAll()
                .requestMatchers("/api/applications/**").hasAnyRole("USER")
                .anyRequest().permitAll()
            );

        return http.build();
    }
}
