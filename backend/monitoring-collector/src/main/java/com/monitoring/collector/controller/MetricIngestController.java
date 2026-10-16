package com.monitoring.collector.controller;

import com.monitoring.collector.dto.MetricIngestRequest;
import com.monitoring.collector.service.MetricIngestService;
import com.monitoring.storage.entity.Application;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingestion-эндпоинт для агентов.
 *
 * <p>Аутентификация — по API-ключу в заголовке {@code X-API-Key}
 * (см. {@code ApiKeyAuthFilter}); JWT для него не требуется.
 * Приложение-владелец ключа извлекается из {@link SecurityContextHolder}.
 */
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
public class MetricIngestController {

    private final MetricIngestService metricIngestService;

    @PostMapping("/metrics")
    public ResponseEntity<Void> ingestMetrics(@Valid @RequestBody MetricIngestRequest request) {
        Application application = currentApplication();
        metricIngestService.ingest(application, request);
        return ResponseEntity.accepted().build();
    }

    private Application currentApplication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Application application) {
            return application;
        }
        throw new IllegalStateException("No authenticated application in security context");
    }
}
