package com.monitoring.collector.integration;

import com.monitoring.collector.dto.request.CreateSavedLogViewRequest;
import com.monitoring.collector.dto.request.SavedLogViewQuery;
import com.monitoring.collector.dto.response.SavedLogViewResponse;
import com.monitoring.collector.dto.request.UpdateSavedLogViewRequest;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.repository.SavedLogViewRepository;
import com.monitoring.collector.service.SavedLogViewService;
import com.monitoring.collector.testsupport.CollectorTestApplication;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises saved log views against the real Flyway schema: persistence of the JSON query, the
 * owner scope and cross-tenant isolation.
 */
@SpringBootTest(classes = CollectorTestApplication.class)
class SavedLogViewIntegrationTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_OWNER_ID = 2L;

    @Autowired
    private SavedLogViewService savedLogViewService;

    @Autowired
    private SavedLogViewRepository savedLogViewRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    private Application ownedApplication;
    private Application foreignApplication;

    @BeforeEach
    void setUp() {
        savedLogViewRepository.deleteAll();
        applicationRepository.deleteAll();
        ownedApplication = applicationRepository.save(Application.builder()
                .name("owned-app")
                .apiKeyHash("owned-key-hash")
                .organizationId(OWNER_ID)
                .build());
        foreignApplication = applicationRepository.save(Application.builder()
                .name("foreign-app")
                .apiKeyHash("foreign-key-hash")
                .organizationId(OTHER_OWNER_ID)
                .build());
    }

    private CreateSavedLogViewRequest request(Long applicationId, String name, String searchText) {
        return CreateSavedLogViewRequest.builder()
                .name(name)
                .queryParams(SavedLogViewQuery.builder()
                        .applicationId(applicationId)
                        .level(LogLevel.ERROR)
                        .searchText(searchText)
                        .timeRange(SavedLogViewQuery.TimeRange.builder()
                                .from(Instant.parse("2026-10-09T00:00:00Z"))
                                .to(Instant.parse("2026-10-09T01:00:00Z"))
                                .build())
                        .build())
                .build();
    }

    @Test
    void create_persistsAndRoundTripsQueryJson() {
        SavedLogViewResponse created = savedLogViewService.create(
                OWNER_ID, request(ownedApplication.getId(), "Errors", "boom"));

        assertEquals("Errors", created.getName());
        assertEquals(LogLevel.ERROR, created.getQueryParams().getLevel());
        assertEquals("boom", created.getQueryParams().getSearchText());
        assertEquals(Instant.parse("2026-10-09T00:00:00Z"), created.getQueryParams().getTimeRange().getFrom());

        List<SavedLogViewResponse> listed = savedLogViewService.list(OWNER_ID);
        assertEquals(1, listed.size());
        assertEquals(created.getId(), listed.get(0).getId());
        assertEquals("boom", listed.get(0).getQueryParams().getSearchText());
    }

    @Test
    void create_persistsNullSearchText() {
        SavedLogViewResponse created = savedLogViewService.create(
                OWNER_ID, request(ownedApplication.getId(), "All errors", "   "));

        assertNull(created.getQueryParams().getSearchText());
    }

    @Test
    void create_rejectsApplicationOwnedByAnotherTenant() {
        assertThrows(ApplicationAccessDeniedException.class,
                () -> savedLogViewService.create(
                        OWNER_ID, request(foreignApplication.getId(), "Errors", null)));

        assertEquals(0, savedLogViewRepository.count());
    }

    @Test
    void list_isScopedToOwner() {
        savedLogViewService.create(OWNER_ID, request(ownedApplication.getId(), "Mine", null));

        assertEquals(1, savedLogViewService.list(OWNER_ID).size());
        assertEquals(0, savedLogViewService.list(OTHER_OWNER_ID).size());
    }

    @Test
    void rename_rejectsForeignView() {
        SavedLogViewResponse created = savedLogViewService.create(
                OWNER_ID, request(ownedApplication.getId(), "Mine", null));

        assertThrows(com.monitoring.collector.exception.SavedLogViewAccessDeniedException.class,
                () -> savedLogViewService.rename(OTHER_OWNER_ID, created.getId(),
                        UpdateSavedLogViewRequest.builder().name("Stolen").build()));

        assertEquals("Mine", savedLogViewService.list(OWNER_ID).get(0).getName());
    }

    @Test
    void delete_removesViewOnlyForOwner() {
        SavedLogViewResponse created = savedLogViewService.create(
                OWNER_ID, request(ownedApplication.getId(), "Mine", null));

        savedLogViewService.delete(OWNER_ID, created.getId());

        assertEquals(0, savedLogViewRepository.count());
    }
}
