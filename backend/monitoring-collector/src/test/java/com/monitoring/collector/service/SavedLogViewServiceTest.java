package com.monitoring.collector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.monitoring.collector.dto.request.CreateSavedLogViewRequest;
import com.monitoring.collector.dto.request.SavedLogViewQuery;
import com.monitoring.collector.dto.response.SavedLogViewResponse;
import com.monitoring.collector.dto.request.UpdateSavedLogViewRequest;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.entity.SavedLogView;
import com.monitoring.collector.exception.SavedLogViewAccessDeniedException;
import com.monitoring.collector.exception.SavedLogViewLimitExceededException;
import com.monitoring.collector.exception.SavedLogViewNotFoundException;
import com.monitoring.collector.repository.SavedLogViewRepository;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.exception.ApplicationAccessDeniedException;
import com.monitoring.apps.service.ApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SavedLogViewServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long OTHER_USER_ID = 99L;
    private static final Long APP_ID = 7L;
    private static final Long VIEW_ID = 5L;
    private static final Instant FROM = Instant.parse("2026-10-09T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-09T01:00:00Z");

    private SavedLogViewRepository repository;
    private ApplicationService applicationService;
    private ObjectMapper objectMapper;
    private SavedLogViewService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(SavedLogViewRepository.class);
        applicationService = Mockito.mock(ApplicationService.class);
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        service = new SavedLogViewService(repository, applicationService, objectMapper);

        when(repository.save(any(SavedLogView.class))).thenAnswer(invocation -> {
            SavedLogView view = invocation.getArgument(0);
            if (view.getId() == null) {
                view.setId(VIEW_ID);
            }
            return view;
        });
    }

    private SavedLogViewQuery query(String searchText) {
        return SavedLogViewQuery.builder()
                .applicationId(APP_ID)
                .level(LogLevel.ERROR)
                .searchText(searchText)
                .timeRange(SavedLogViewQuery.TimeRange.builder().from(FROM).to(TO).build())
                .build();
    }

    private CreateSavedLogViewRequest createRequest(String name, String searchText) {
        return CreateSavedLogViewRequest.builder().name(name).queryParams(query(searchText)).build();
    }

    private SavedLogView storedView(Long organizationId, String queryJson) {
        return SavedLogView.builder()
                .id(VIEW_ID)
                .organizationId(organizationId)
                .name("Errors")
                .queryParams(queryJson)
                .createdAt(FROM)
                .updatedAt(TO)
                .build();
    }

    private String json(SavedLogViewQuery query) {
        try {
            return objectMapper.writeValueAsString(query);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void list_returnsOwnOwnerViewsInRepositoryOrder() {
        when(repository.findByOrganizationIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of(storedView(USER_ID, json(query("boom")))));

        List<SavedLogViewResponse> result = service.list(USER_ID);

        assertEquals(1, result.size());
        assertEquals(VIEW_ID, result.get(0).getId());
        assertEquals("Errors", result.get(0).getName());
        assertEquals(APP_ID, result.get(0).getQueryParams().getApplicationId());
        assertEquals(LogLevel.ERROR, result.get(0).getQueryParams().getLevel());
        assertEquals("boom", result.get(0).getQueryParams().getSearchText());
        assertEquals(FROM, result.get(0).getQueryParams().getTimeRange().getFrom());
    }

    @Test
    void create_verifiesApplicationOwnership_andTrimsName() {
        when(applicationService.getOwnedApplication(APP_ID, USER_ID)).thenReturn(application());
        when(repository.countByOrganizationId(USER_ID)).thenReturn(0L);

        SavedLogViewResponse response = service.create(USER_ID, createRequest("  Errors  ", "boom"));

        verify(applicationService).getOwnedApplication(APP_ID, USER_ID);
        assertEquals("Errors", response.getName());
        assertEquals(APP_ID, response.getQueryParams().getApplicationId());
        assertEquals("boom", response.getQueryParams().getSearchText());
    }

    @Test
    void create_normalizesBlankSearchTextToNull() {
        when(applicationService.getOwnedApplication(APP_ID, USER_ID)).thenReturn(application());
        when(repository.countByOrganizationId(USER_ID)).thenReturn(0L);

        SavedLogViewResponse response = service.create(USER_ID, createRequest("Errors", "   "));

        assertNull(response.getQueryParams().getSearchText());
    }

    @Test
    void create_propagatesAccessDeniedWhenApplicationNotOwned() {
        when(applicationService.getOwnedApplication(APP_ID, USER_ID))
                .thenThrow(new ApplicationAccessDeniedException());

        assertThrows(ApplicationAccessDeniedException.class,
                () -> service.create(USER_ID, createRequest("Errors", null)));

        verifyNoInteractions(repository);
    }

    @Test
    void create_rejectsWhenOwnerReachedLimit() {
        when(applicationService.getOwnedApplication(APP_ID, USER_ID)).thenReturn(application());
        when(repository.countByOrganizationId(USER_ID)).thenReturn((long) SavedLogViewService.MAX_SAVED_VIEWS_PER_OWNER);

        assertThrows(SavedLogViewLimitExceededException.class,
                () -> service.create(USER_ID, createRequest("Errors", null)));

        verify(repository, never()).save(any());
    }

    @Test
    void rename_updatesNameForOwnedView() {
        when(repository.findById(VIEW_ID)).thenReturn(java.util.Optional.of(storedView(USER_ID, json(query(null)))));

        SavedLogViewResponse response = service.rename(USER_ID, VIEW_ID,
                UpdateSavedLogViewRequest.builder().name("  Renamed  ").build());

        assertEquals("Renamed", response.getName());
        verify(repository).save(any(SavedLogView.class));
    }

    @Test
    void rename_throwsNotFoundForMissingView() {
        when(repository.findById(VIEW_ID)).thenReturn(java.util.Optional.empty());

        assertThrows(SavedLogViewNotFoundException.class,
                () -> service.rename(USER_ID, VIEW_ID, UpdateSavedLogViewRequest.builder().name("x").build()));

        verify(repository, never()).save(any());
    }

    @Test
    void rename_throwsAccessDeniedForForeignView() {
        when(repository.findById(VIEW_ID))
                .thenReturn(java.util.Optional.of(storedView(OTHER_USER_ID, json(query(null)))));

        assertThrows(SavedLogViewAccessDeniedException.class,
                () -> service.rename(USER_ID, VIEW_ID, UpdateSavedLogViewRequest.builder().name("x").build()));

        verify(repository, never()).save(any());
    }

    @Test
    void delete_removesOwnedView() {
        SavedLogView owned = storedView(USER_ID, json(query(null)));
        when(repository.findById(VIEW_ID)).thenReturn(java.util.Optional.of(owned));

        service.delete(USER_ID, VIEW_ID);

        verify(repository).delete(owned);
    }

    @Test
    void delete_throwsAccessDeniedForForeignView() {
        when(repository.findById(VIEW_ID))
                .thenReturn(java.util.Optional.of(storedView(OTHER_USER_ID, json(query(null)))));

        assertThrows(SavedLogViewAccessDeniedException.class, () -> service.delete(USER_ID, VIEW_ID));

        verify(repository, never()).delete(any(SavedLogView.class));
    }

    private Application application() {
        return Application.builder()
                .id(APP_ID)
                .name("my-app")
                .apiKeyHash("app_key_hash")
                .organizationId(USER_ID)
                .build();
    }
}
