package com.monitoring.api;

import com.monitoring.collector.config.LogFacetProperties;
import com.monitoring.collector.dto.request.LogsQueryRequest;
import com.monitoring.collector.dto.response.LogEventResponse;
import com.monitoring.collector.dto.response.LogFacetValueResponse;
import com.monitoring.collector.dto.response.LogFacetsResponse;
import com.monitoring.collector.entity.LogEvent;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.exception.InvalidLogFacetRequestException;
import com.monitoring.collector.facet.LogFacetField;
import com.monitoring.collector.facet.LogFacetFields;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.collector.service.LogFacetService;
import com.monitoring.collector.service.LogQueryService;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.exception.ApplicationAccessDeniedException;
import com.monitoring.apps.repository.ApplicationRepository;
import com.monitoring.common.dto.response.PageResponse;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the log facet aggregation against a real persistence context: the counts have to
 * describe exactly the rows the log explorer shows, the filters have to narrow that row set and
 * a caller must not reach another tenant's logs.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:logs-facets-it;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "monitoring.logs.facets.max-requests=1000",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.monitoring.api.LogFacetsIntegrationTest$RecordingStatementInspector"
})
class LogFacetsIntegrationTest {

    private static final long OWNER_ID = 1L;
    private static final Instant FROM = Instant.parse("2026-10-09T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-09T23:59:59Z");

    @Autowired
    private LogFacetService logFacetService;

    @Autowired
    private LogQueryService logQueryService;

    @Autowired
    private LogEventRepository logEventRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private LogFacetProperties facetProperties;

    private Application application;

    @BeforeEach
    void setUp() {
        logEventRepository.deleteAll();
        applicationRepository.deleteAll();
        facetProperties.setMaxScanRows(50_000);
        application = applicationRepository.save(Application.builder()
                .name("facets-it-app")
                .apiKeyHash("app_facets_it")
                .organizationId(OWNER_ID)
                .build());
    }

    private void saveLog(LogLevel level, String message, String context, Instant timestamp) {
        logEventRepository.save(LogEvent.builder()
                .application(application)
                .level(level)
                .message(message)
                .context(context)
                .timestamp(timestamp)
                .receivedAt(Instant.now())
                .build());
    }

    private void seedTypicalData() {
        String prod = "{\"host\":\"server-1\",\"service\":\"api\",\"k8s.namespace\":\"prod\"}";
        String prodTwo = "{\"host\":\"server-2\",\"service\":\"api\",\"k8s.namespace\":\"prod\"}";
        String staging = "{\"host\":\"server-1\",\"service\":\"worker\"}";

        saveLog(LogLevel.ERROR, "boom-1", prod, Instant.parse("2026-10-09T10:00:00Z"));
        saveLog(LogLevel.ERROR, "boom-2", prod, Instant.parse("2026-10-09T10:01:00Z"));
        saveLog(LogLevel.ERROR, "boom-3", prodTwo, Instant.parse("2026-10-09T10:02:00Z"));
        saveLog(LogLevel.WARN, "slow", prod, Instant.parse("2026-10-09T10:03:00Z"));
        saveLog(LogLevel.INFO, "started", staging, Instant.parse("2026-10-09T10:04:00Z"));
    }

    private LogsQueryRequest request() {
        return request(null, null, null);
    }

    private LogsQueryRequest request(LogLevel level, String host, String searchText) {
        return LogsQueryRequest.builder()
                .applicationId(application.getId())
                .level(level)
                .from(FROM)
                .to(TO)
                .host(host)
                .searchText(searchText)
                .page(0)
                .size(25)
                .build();
    }

    private LogFacetsResponse facets(List<LogFacetField> fields, LogsQueryRequest request) {
        return logFacetService.facets(OWNER_ID, request, fields, 5);
    }

    @Test
    void aggregatesLevelCountsFromTheIndexedColumn() {
        seedTypicalData();

        LogFacetsResponse response = facets(List.of(LogFacetField.LEVEL), request());

        assertFalse(response.approximate());
        // Equal counts are ordered by value, so the order is deterministic.
        assertEquals(List.of("ERROR", "INFO", "WARN"),
                response.facets().get("level").stream().map(LogFacetValueResponse::value).toList());
        assertEquals(3, response.facets().get("level").get(0).count());
        assertEquals(1, response.facets().get("level").get(1).count());
        assertEquals(1, response.facets().get("level").get(2).count());
    }

    @Test
    void aggregatesContextFieldsFromTheJsonDocument() {
        seedTypicalData();

        LogFacetsResponse response = facets(
                List.of(LogFacetField.HOST, LogFacetField.SERVICE, LogFacetField.NAMESPACE), request());

        assertEquals(List.of("server-1", "server-2"),
                response.facets().get("host").stream().map(LogFacetValueResponse::value).toList());
        assertEquals(4, response.facets().get("host").get(0).count());
        assertEquals(1, response.facets().get("host").get(1).count());

        assertEquals(List.of("api", "worker"),
                response.facets().get("service").stream().map(LogFacetValueResponse::value).toList());
        assertEquals(4, response.facets().get("service").get(0).count());
        assertEquals(1, response.facets().get("service").get(1).count());

        assertEquals(List.of("prod"),
                response.facets().get("k8s.namespace").stream().map(LogFacetValueResponse::value).toList());
        assertEquals(4, response.facets().get("k8s.namespace").get(0).count());
    }

    @Test
    void facetCountsMatchTheNumberOfMatchingRows() {
        seedTypicalData();

        LogFacetsResponse response = facets(List.of(LogFacetField.LEVEL, LogFacetField.HOST), request());
        PageResponse<LogEventResponse> page = logQueryService.query(OWNER_ID, request());

        long facetTotal = response.facets().get("level").stream()
                .mapToLong(LogFacetValueResponse::count).sum();
        assertEquals(page.totalElements(), facetTotal);
    }

    @Test
    void facetCountsReflectTheActiveFilters() {
        seedTypicalData();

        LogFacetsResponse response = facets(
                List.of(LogFacetField.HOST), request(LogLevel.ERROR, null, null));

        assertEquals(List.of("server-1", "server-2"),
                response.facets().get("host").stream().map(LogFacetValueResponse::value).toList());
        assertEquals(2, response.facets().get("host").get(0).count());
        assertEquals(1, response.facets().get("host").get(1).count());
    }

    @Test
    void hostFilterNarrowsTheResultList() {
        seedTypicalData();

        PageResponse<LogEventResponse> page = logQueryService.query(OWNER_ID, request(null, "server-1", null));

        assertEquals(4, page.totalElements());
        assertTrue(page.content().stream().allMatch(log -> log.getContext().contains("server-1")));
    }

    @Test
    void serviceAndNamespaceFiltersNarrowTheResultList() {
        seedTypicalData();

        assertEquals(5, logQueryService.query(OWNER_ID, request(null, null, null)).totalElements());
        assertEquals(1, logQueryService.query(OWNER_ID, withService("worker")).totalElements());
        assertEquals(4, logQueryService.query(OWNER_ID, withNamespace("prod")).totalElements());
        assertEquals(0, logQueryService.query(OWNER_ID, withNamespace("staging")).totalElements());
    }

    @Test
    void filtersValuesThatNeedJsonEscaping() {
        // The agent stores the escaped form; the facet shows the decoded value and the filter
        // has to match the stored text again.
        saveLog(LogLevel.ERROR, "quoted", "{\"host\":\"srv\\\"1\"}", Instant.parse("2026-10-09T10:00:00Z"));
        saveLog(LogLevel.ERROR, "ampersand", "{\"host\":\"a\\u0026b\"}", Instant.parse("2026-10-09T10:01:00Z"));
        saveLog(LogLevel.ERROR, "plain", "{\"host\":\"server-1\"}", Instant.parse("2026-10-09T10:02:00Z"));

        LogFacetsResponse response = facets(List.of(LogFacetField.HOST), request());
        assertEquals(List.of("a&b", "server-1", "srv\"1"),
                response.facets().get("host").stream().map(LogFacetValueResponse::value).toList());

        assertEquals(1, logQueryService.query(OWNER_ID, request(null, "srv\"1", null)).totalElements());
        assertEquals(1, logQueryService.query(OWNER_ID, request(null, "a&b", null)).totalElements());
    }

    @Test
    void excludesRowsWithoutAValue() {
        saveLog(LogLevel.ERROR, "no context", null, Instant.parse("2026-10-09T10:00:00Z"));
        saveLog(LogLevel.ERROR, "not json", "plain text", Instant.parse("2026-10-09T10:01:00Z"));
        saveLog(LogLevel.ERROR, "empty value", "{\"host\":\"\"}", Instant.parse("2026-10-09T10:02:00Z"));
        saveLog(LogLevel.ERROR, "with host", "{\"host\":\"server-1\"}", Instant.parse("2026-10-09T10:03:00Z"));

        LogFacetsResponse response = facets(List.of(LogFacetField.HOST), request());

        assertEquals(List.of("server-1"),
                response.facets().get("host").stream().map(LogFacetValueResponse::value).toList());
        assertEquals(1, response.facets().get("host").get(0).count());
    }

    @Test
    void marksTheResultAsApproximateWhenTheScanLimitIsReached() {
        saveLog(LogLevel.ERROR, "first", "{\"host\":\"server-1\"}", Instant.parse("2026-10-09T10:00:00Z"));
        saveLog(LogLevel.ERROR, "second", "{\"host\":\"server-1\"}", Instant.parse("2026-10-09T10:01:00Z"));
        saveLog(LogLevel.ERROR, "third", "{\"host\":\"server-2\"}", Instant.parse("2026-10-09T10:02:00Z"));

        facetProperties.setMaxScanRows(2);
        LogFacetsResponse response = facets(List.of(LogFacetField.HOST), request());

        assertTrue(response.approximate());
        assertEquals(2, response.scanLimit());
        // Only the two most recent rows were scanned.
        assertEquals(1, response.facets().get("host").get(0).count());
    }

    @Test
    void rejectsAForeignApplication() {
        Application other = applicationRepository.save(Application.builder()
                .name("foreign-facets-app")
                .apiKeyHash("app_foreign_facets")
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
                () -> logFacetService.facets(OWNER_ID, foreign, List.of(LogFacetField.LEVEL), 5));
    }

    @Test
    void rejectsUnknownFields() {
        InvalidLogFacetRequestException error = assertThrows(InvalidLogFacetRequestException.class,
                () -> LogFacetFields.parse("password", LogFacetService.MAX_FIELDS));

        assertTrue(error.getMessage().contains("password"));
        assertTrue(error.getMessage().contains("level"));
    }

    @Test
    void rejectsMoreFieldsThanAllowed() {
        assertThrows(InvalidLogFacetRequestException.class,
                () -> LogFacetFields.parse("level,host,service,k8s.namespace,extra",
                        LogFacetService.MAX_FIELDS));
    }

    @Test
    void rejectsAnEmptyFieldList() {
        assertThrows(InvalidLogFacetRequestException.class,
                () -> LogFacetFields.parse(" , ", LogFacetService.MAX_FIELDS));
        assertThrows(InvalidLogFacetRequestException.class,
                () -> LogFacetFields.parse(null, LogFacetService.MAX_FIELDS));
    }

    @Test
    void returnsOnlyTheRequestedFields() {
        seedTypicalData();

        LogFacetsResponse response = facets(List.of(LogFacetField.SERVICE), request());

        assertEquals(Map.of("service", response.facets().get("service")), response.facets());
    }

    @Test
    void bindsTheFilterValuesAsParameters() {
        seedTypicalData();
        RecordingStatementInspector.STATEMENTS.clear();

        logQueryService.query(OWNER_ID, request(LogLevel.ERROR, "server-1", "boom"));

        List<String> statements = List.copyOf(RecordingStatementInspector.STATEMENTS);
        assertTrue(statements.stream().anyMatch(statement -> statement.contains("locate(")),
                () -> "no LOCATE statement was executed: " + statements);
        assertTrue(statements.stream().anyMatch(statement -> statement.contains("like")),
                () -> "no LIKE statement was executed: " + statements);
        // The filter values are bind parameters, never part of the SQL text.
        assertTrue(statements.stream().noneMatch(statement -> statement.contains("server-1")),
                () -> "the host filter was inlined: " + statements);
        assertTrue(statements.stream().noneMatch(statement -> statement.contains("boom")),
                () -> "the search text was inlined: " + statements);
    }

    private LogsQueryRequest withService(String service) {
        LogsQueryRequest request = request();
        request.setService(service);
        return request;
    }

    private LogsQueryRequest withNamespace(String namespace) {
        LogsQueryRequest request = request();
        request.setNamespace(namespace);
        return request;
    }

    /** Records the SQL statements so the tests can assert on the generated queries. */
    public static class RecordingStatementInspector implements StatementInspector {

        static final List<String> STATEMENTS = Collections.synchronizedList(new ArrayList<>());

        @Override
        public String inspect(String sql) {
            STATEMENTS.add(sql);
            return sql;
        }
    }
}
