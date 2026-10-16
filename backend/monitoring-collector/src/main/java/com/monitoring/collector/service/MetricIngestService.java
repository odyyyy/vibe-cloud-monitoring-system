package com.monitoring.collector.service;

import com.monitoring.collector.dto.MetricIngestRequest;
import com.monitoring.collector.dto.MetricPoint;
import com.monitoring.collector.entity.MetricSample;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.storage.entity.Application;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Приём метрик от агента и сохранение их с привязкой к приложению.
 *
 * <p>Payload валидируется на границе (контроллер, {@code @Valid}) до вызова сервиса:
 * пустой батч, превышение лимита в 1000 точек и некорректные поля отклоняются с HTTP 400.
 *
 * <p>Сохранение синхронное: HTTP 202 возвращается после того, как данные записаны,
 * поэтому ответ означает, что батч действительно сохранён, а не только принят в очередь.
 * Асинхронный пайплайн с durable-очередью — отдельный этап.
 */
@Service
@RequiredArgsConstructor
public class MetricIngestService {

    private static final Logger log = LoggerFactory.getLogger(MetricIngestService.class);

    private final MetricSampleRepository metricSampleRepository;

    @Transactional
    public int ingest(Application application, MetricIngestRequest request) {
        Instant receivedAt = Instant.now();
        List<MetricSample> samples = new ArrayList<>(request.getMetrics().size());

        for (MetricPoint point : request.getMetrics()) {
            samples.add(MetricSample.builder()
                    .application(application)
                    .name(point.getName())
                    .value(point.getValue())
                    .timestamp(point.getTimestamp())
                    .receivedAt(receivedAt)
                    .tags(point.getTags() == null ? new HashMap<>() : new HashMap<>(point.getTags()))
                    .build());
        }

        metricSampleRepository.saveAll(samples);

        // Логируем факт приёма и объём, но не сами метрики и не значения тегов.
        log.info("Accepted {} metric samples for application id={}", samples.size(), application.getId());

        return samples.size();
    }
}
