package com.monitoring.api;

import com.monitoring.collector.dto.response.LogEventResponse;
import com.monitoring.collector.dto.request.LogsQueryRequest;
import com.monitoring.collector.export.ExportData;
import com.monitoring.common.dto.response.PageResponse;
import com.monitoring.collector.entity.LogEvent;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.collector.service.LogQueryService;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.exception.ApplicationAccessDeniedException;
import com.monitoring.apps.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies log query filtering, ordering, paging and tenant isolation against a real
 * persistence context.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:logs-query-it;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class LogsQueryIntegrationTest {

    private static final long OWNER_ID = 1L;
    private static final Instant FROM = Instant.parse("2026-10-09T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-09T23:59:59Z");

    @Autowired
    private LogQueryService logQueryService;

    @Autowired
    private LogEventRepository logEventRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    private Application application;

    @BeforeEach
    void setUp() {
        logEventRepository.deleteAll();
        applicationRepository.deleteAll();
        application = applicationRepository.save(Application.builder()
                .name("logs-it-app")
                .apiKeyHash("app_logs_it")
                .organizationId(OWNER_ID)
                .build());
    }

    private void saveLog(Application app, LogLevel level, String message, Instant timestamp) {
        logEventRepository.save(LogEvent.builder()
                .application(app)
                .level(level)
                .message(message)
                .context("{\"k\":\"v\"}")
                .timestamp(timestamp)
                .receivedAt(Instant.now())
                .build());
    }

    private LogsQueryRequest request(LogLevel level, String searchText, int page, int size) {
        return LogsQueryRequest.builder()
                .applicationId(application.getId())
                .level(level)
                .from(FROM)
                .to(TO)
                .searchText(searchText)
                .page(page)
                .size(size)
                .build();
    }

    @Test
    void filtersByLevel() {
        saveLog(application, LogLevel.INFO, "started", Instant.parse("2026-10-09T10:00:00Z"));
        saveLog(application, LogLevel.ERROR, "boom", Instant.parse("2026-10-09T10:01:00Z"));

        PageResponse<LogEventResponse> page = logQueryService.query(OWNER_ID, request(LogLevel.ERROR, null, 0, 25));

        assertEquals(1, page.totalElements());
        assertEquals("boom", page.content().get(0).getMessage());
    }

    @Test
    void filtersBySearchTextCaseInsensitiveAndLiteral() {
        saveLog(application, LogLevel.INFO, "Connection Refused", Instant.parse("2026-10-09T10:00:00Z"));
        saveLog(application, LogLevel.INFO, "all good", Instant.parse("2026-10-09T10:01:00Z"));
        saveLog(application, LogLevel.INFO, "100% ready", Instant.parse("2026-10-09T10:02:00Z"));

        PageResponse<LogEventResponse> page = logQueryService.query(OWNER_ID, request(null, "refused", 0, 25));
        assertEquals(1, page.totalElements());

        // '%' is matched literally, not as a wildcard.
        PageResponse<LogEventResponse> percent = logQueryService.query(OWNER_ID, request(null, "100%", 0, 25));
        assertEquals(1, percent.totalElements());
    }

    @Test
    void ordersByTimestampDescendingWithIdTieBreaker() {
        saveLog(application, LogLevel.INFO, "first", Instant.parse("2026-10-09T10:00:00Z"));
        saveLog(application, LogLevel.INFO, "second", Instant.parse("2026-10-09T10:00:00Z"));
        saveLog(application, LogLevel.INFO, "third", Instant.parse("2026-10-09T11:00:00Z"));

        PageResponse<LogEventResponse> page = logQueryService.query(OWNER_ID, request(null, null, 0, 25));

        assertEquals(List.of("third", "second", "first"),
                page.content().stream().map(LogEventResponse::getMessage).toList());
    }

    @Test
    void paginates() {
        for (int i = 0; i < 5; i++) {
            saveLog(application, LogLevel.INFO, "log-" + i, Instant.parse("2026-10-09T10:0" + i + ":00Z"));
        }

        PageResponse<LogEventResponse> page = logQueryService.query(OWNER_ID, request(null, null, 1, 2));

        assertEquals(5, page.totalElements());
        assertEquals(3, page.totalPages());
        assertEquals(1, page.page());
        assertEquals(2, page.size());
        assertEquals(2, page.content().size());
    }

    @Test
    void isolatesByApplication() {
        Application other = applicationRepository.save(Application.builder()
                .name("other-logs-app")
                .apiKeyHash("app_other_logs")
                .organizationId(2L)
                .build());
        saveLog(application, LogLevel.INFO, "mine", Instant.parse("2026-10-09T10:00:00Z"));
        saveLog(other, LogLevel.INFO, "theirs", Instant.parse("2026-10-09T10:00:00Z"));

        PageResponse<LogEventResponse> page = logQueryService.query(OWNER_ID, request(null, null, 0, 25));

        assertEquals(1, page.totalElements());
        assertEquals("mine", page.content().get(0).getMessage());
    }

    @Test
    void queryingForeignApplication_throwsAccessDenied() {
        Application other = applicationRepository.save(Application.builder()
                .name("foreign-app")
                .apiKeyHash("app_foreign")
                .organizationId(2L)
                .build());

        LogsQueryRequest foreign = LogsQueryRequest.builder()
                .applicationId(other.getId())
                .from(FROM)
                .to(TO)
                .page(0)
                .size(25)
                .build();

        assertThrows(ApplicationAccessDeniedException.class, () -> logQueryService.query(OWNER_ID, foreign));
    }

    @Test
    void excludesEventsOutsideRange() {
        saveLog(application, LogLevel.INFO, "outside", Instant.parse("2026-10-01T10:00:00Z"));
        saveLog(application, LogLevel.INFO, "inside", Instant.parse("2026-10-09T10:00:00Z"));

        PageResponse<LogEventResponse> page = logQueryService.query(OWNER_ID, request(null, null, 0, 25));

        assertEquals(1, page.totalElements());
        assertEquals("inside", page.content().get(0).getMessage());
    }

    @Test
    void exportRows_truncatesAndReportsTotalMatchingRows() {
        saveLog(application, LogLevel.INFO, "first", Instant.parse("2026-10-09T10:00:00Z"));
        saveLog(application, LogLevel.INFO, "second", Instant.parse("2026-10-09T10:01:00Z"));
        saveLog(application, LogLevel.INFO, "third", Instant.parse("2026-10-09T10:02:00Z"));

        ExportData<LogEventResponse> data =
                logQueryService.exportRows(OWNER_ID, request(null, null, 0, 25), 2);

        assertEquals(2, data.rows().size());
        assertEquals(3, data.totalRows());
        assertTrue(data.isTruncated());
        assertEquals("third", data.rows().get(0).getMessage());
        assertEquals("second", data.rows().get(1).getMessage());
    }

    @Test
    void exportRows_returnsAllRowsWhenUnderLimit() {
        saveLog(application, LogLevel.INFO, "only", Instant.parse("2026-10-09T10:00:00Z"));

        ExportData<LogEventResponse> data =
                logQueryService.exportRows(OWNER_ID, request(null, null, 0, 25), 10);

        assertEquals(1, data.rows().size());
        assertEquals(1, data.totalRows());
        assertFalse(data.isTruncated());
    }

    @Test
    void exportRows_rejectsForeignApplication() {
        Application other = applicationRepository.save(Application.builder()
                .name("foreign-export-app")
                .apiKeyHash("app_foreign_export")
                .organizationId(2L)
                .build());

        LogsQueryRequest foreign = LogsQueryRequest.builder()
                .applicationId(other.getId())
                .from(FROM)
                .to(TO)
                .page(0)
                .size(25)
                .build();

        assertThrows(ApplicationAccessDeniedException.class,
                () -> logQueryService.exportRows(OWNER_ID, foreign, 10));
    }
}
