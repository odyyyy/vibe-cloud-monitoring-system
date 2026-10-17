package com.monitoring.collector.service;

import com.monitoring.collector.dto.MetricIngestRequest;
import com.monitoring.collector.dto.MetricPoint;
import com.monitoring.collector.entity.MetricSample;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.storage.entity.Application;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricIngestServiceTest {

    private MetricSampleRepository repository;
    private MetricIngestService service;
    private Application application;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(MetricSampleRepository.class);
        service = new MetricIngestService(repository);
        application = Application.builder()
                .id(7L)
                .name("my-app")
                .apiKeyHash("app_key_hash")
                .ownerId(1L)
                .build();
    }

    @Test
    void ingest_mapsPointsAndSavesAllBoundToApplication() {
        Instant ts = Instant.parse("2026-10-09T10:00:00Z");
        MetricPoint point = MetricPoint.builder()
                .name("cpu_usage")
                .value(75.5)
                .timestamp(ts)
                .tags(Map.of("host", "server-1"))
                .build();
        MetricIngestRequest request = MetricIngestRequest.builder()
                .metrics(List.of(point))
                .build();
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        int accepted = service.ingest(application, request);

        assertEquals(1, accepted);
        ArgumentCaptor<List<MetricSample>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        List<MetricSample> saved = captor.getValue();
        assertEquals(1, saved.size());
        MetricSample sample = saved.get(0);
        assertEquals(7L, sample.getApplication().getId());
        assertEquals("cpu_usage", sample.getName());
        assertEquals(75.5, sample.getValue());
        assertEquals(ts, sample.getTimestamp());
        assertEquals("server-1", sample.getTags().get("host"));
        assertNotNull(sample.getReceivedAt());
    }

    @Test
    void ingest_nullTags_mapsToEmptyMap() {
        MetricPoint point = MetricPoint.builder()
                .name("mem_usage")
                .value(50.0)
                .timestamp(Instant.parse("2026-10-09T10:00:00Z"))
                .tags(null)
                .build();
        MetricIngestRequest request = MetricIngestRequest.builder().metrics(List.of(point)).build();
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service.ingest(application, request);

        ArgumentCaptor<List<MetricSample>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertTrue(captor.getValue().get(0).getTags().isEmpty());
    }
}
