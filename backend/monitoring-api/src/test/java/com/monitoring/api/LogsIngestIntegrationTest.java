package com.monitoring.api;

import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.storage.entity.Application;
import com.monitoring.storage.repository.ApplicationRepository;
import com.monitoring.storage.security.ApiKeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the log ingestion path end to end: the ingest security chain (X-API-Key) plus
 * validation and persistence.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:logs-ingest-it;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class LogsIngestIntegrationTest {

    private static final String VALID_BODY = "{\"events\":[{\"level\":\"ERROR\",\"message\":\"boom\","
            + "\"timestamp\":\"2026-10-09T10:00:00Z\",\"context\":\"{\\\"user\\\":\\\"alice\\\"}\"}]}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private LogEventRepository logEventRepository;

    @BeforeEach
    void setUp() {
        logEventRepository.deleteAll();
        applicationRepository.deleteAll();
        applicationRepository.save(Application.builder()
                .name("logs-ingest-app")
                .apiKeyHash(ApiKeyHasher.hash("app_logs_ingest"))
                .ownerId(1L)
                .build());
    }

    @Test
    void ingest_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingest_withValidApiKey_persistsAndReturns202() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/logs")
                        .header("X-API-Key", "app_logs_ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isAccepted());

        assertEquals(1, logEventRepository.count());
    }

    @Test
    void ingest_emptyBatch_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/logs")
                        .header("X-API-Key", "app_logs_ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[]}"))
                .andExpect(status().isBadRequest());

        assertEquals(0, logEventRepository.count());
    }

    @Test
    void ingest_unknownLevel_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/logs")
                        .header("X-API-Key", "app_logs_ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[{\"level\":\"CRITICAL\",\"message\":\"x\","
                                + "\"timestamp\":\"2026-10-09T10:00:00Z\"}]}"))
                .andExpect(status().isBadRequest());

        assertEquals(0, logEventRepository.count());
    }

    @Test
    void ingest_blankMessage_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/logs")
                        .header("X-API-Key", "app_logs_ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[{\"level\":\"INFO\",\"message\":\"\","
                                + "\"timestamp\":\"2026-10-09T10:00:00Z\"}]}"))
                .andExpect(status().isBadRequest());

        assertEquals(0, logEventRepository.count());
    }
}
