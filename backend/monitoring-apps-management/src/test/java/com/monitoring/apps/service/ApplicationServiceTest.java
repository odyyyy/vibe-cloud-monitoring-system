package com.monitoring.apps.service;

import com.monitoring.apps.dto.request.ApplicationCreateRequest;
import com.monitoring.apps.dto.response.ApplicationWithKeyResponse;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.exception.ApplicationAccessDeniedException;
import com.monitoring.apps.exception.ApplicationNameConflictException;
import com.monitoring.apps.exception.ApplicationNotFoundException;
import com.monitoring.apps.repository.ApplicationRepository;
import com.monitoring.apps.security.ApiKeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApplicationServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private ApplicationRepository applicationRepository;
    private ApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationRepository = Mockito.mock(ApplicationRepository.class);
        applicationService = new ApplicationService(applicationRepository);
    }

    private ApplicationCreateRequest createRequest(String name) {
        ApplicationCreateRequest request = new ApplicationCreateRequest();
        request.setName(name);
        return request;
    }

    private Application appOwnedBy(Long organizationId) {
        return Application.builder()
                .id(10L)
                .name("my-app")
                .apiKeyHash(ApiKeyHasher.hash("app_old"))
                .organizationId(organizationId)
                .build();
    }

    @Test
    void createApplication_generatesApiKeyAndPersistsOnlyItsHash() {
        when(applicationRepository.existsByNameAndOrganizationId("my-app", OWNER_ID)).thenReturn(false);
        when(applicationRepository.existsByApiKeyHash(any())).thenReturn(false);
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ApplicationWithKeyResponse created = applicationService.createApplication(createRequest("my-app"), OWNER_ID);

        assertNotNull(created.apiKey());
        assertTrue(created.apiKey().startsWith("app_"), "API key must start with app_ prefix");

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(captor.capture());
        Application saved = captor.getValue();
        assertEquals(OWNER_ID, saved.getOrganizationId());
        assertEquals(ApiKeyHasher.hash(created.apiKey()), saved.getApiKeyHash());
        assertNotEquals(created.apiKey(), saved.getApiKeyHash(), "raw key must never be persisted");
    }

    @Test
    void createApplication_duplicateName_throwsConflict() {
        when(applicationRepository.existsByNameAndOrganizationId("my-app", OWNER_ID)).thenReturn(true);

        assertThrows(ApplicationNameConflictException.class,
                () -> applicationService.createApplication(createRequest("my-app"), OWNER_ID));
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void createApplication_sameNameAllowedForDifferentOwner() {
        when(applicationRepository.existsByNameAndOrganizationId("my-app", OTHER_USER_ID)).thenReturn(false);
        when(applicationRepository.existsByApiKeyHash(any())).thenReturn(false);
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        applicationService.createApplication(createRequest("my-app"), OTHER_USER_ID);

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(captor.capture());
        assertEquals(OTHER_USER_ID, captor.getValue().getOrganizationId());
        verify(applicationRepository).existsByNameAndOrganizationId("my-app", OTHER_USER_ID);
    }

    @Test
    void getOwnedApplication_notFound_throwsNotFound() {
        when(applicationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ApplicationNotFoundException.class,
                () -> applicationService.getOwnedApplication(99L, OWNER_ID));
    }

    @Test
    void getOwnedApplication_wrongOwner_throwsAccessDenied() {
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(appOwnedBy(OTHER_USER_ID)));

        assertThrows(ApplicationAccessDeniedException.class,
                () -> applicationService.getOwnedApplication(10L, OWNER_ID));
    }

    @Test
    void getOwnedApplication_owner_returnsApplication() {
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(appOwnedBy(OWNER_ID)));

        Application result = applicationService.getOwnedApplication(10L, OWNER_ID);

        assertEquals(10L, result.getId());
    }

    @Test
    void deleteApplication_wrongOwner_throwsAccessDenied_andDoesNotDelete() {
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(appOwnedBy(OTHER_USER_ID)));

        assertThrows(ApplicationAccessDeniedException.class,
                () -> applicationService.deleteApplication(10L, OWNER_ID));
        verify(applicationRepository, never()).delete(any());
    }

    @Test
    void updateApplication_wrongOwner_throwsAccessDenied() {
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(appOwnedBy(OTHER_USER_ID)));

        assertThrows(ApplicationAccessDeniedException.class,
                () -> applicationService.updateApplication(10L, "new-name", OWNER_ID));
    }

    @Test
    void regenerateApiKey_wrongOwner_throwsAccessDenied() {
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(appOwnedBy(OTHER_USER_ID)));

        assertThrows(ApplicationAccessDeniedException.class,
                () -> applicationService.regenerateApiKey(10L, OWNER_ID));
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void regenerateApiKey_owner_returnsNewPrefixedKeyAndStoresItsHash() {
        Application owned = appOwnedBy(OWNER_ID);
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(owned));
        when(applicationRepository.existsByApiKeyHash(any())).thenReturn(false);
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ApplicationWithKeyResponse updated = applicationService.regenerateApiKey(10L, OWNER_ID);

        assertTrue(updated.apiKey().startsWith("app_"));
        assertEquals(ApiKeyHasher.hash(updated.apiKey()), owned.getApiKeyHash());
        assertNotEquals(ApiKeyHasher.hash("app_old"), owned.getApiKeyHash());
    }
}
