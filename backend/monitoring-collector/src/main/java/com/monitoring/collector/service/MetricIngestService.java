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
 * Accepts metric batches from agents and stores them scoped to an application.
 *
 * <p>The payload is validated at the boundary (controller, {@code @Valid}) before the
 * service is called: an empty batch, more than 1000 points or invalid fields are rejected
 * with HTTP 400.
 *
 * <p>Persistence is synchronous: HTTP 202 is returned only after the data is written, so
 * the response means the batch is really stored, not merely queued. An asynchronous
 * pipeline with a durable queue is a separate stage.
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

        // Log the fact and size of the batch, but never the metrics themselves or tag values.
        log.info("Accepted {} metric samples for application id={}", samples.size(), application.getId());

        return samples.size();
    }
}
