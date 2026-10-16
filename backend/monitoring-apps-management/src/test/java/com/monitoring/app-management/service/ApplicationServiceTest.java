package com.monitoring.storage.service;

import com.monitoring.storage.dto.ApplicationCreateRequest;
import com.monitoring.storage.entity.Application;
import com.monitoring.storage.exception.ApplicationAccessDeniedException;
import com.monitoring.storage.exception.ApplicationNotFoundException;
import com.monitoring.storage.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    private Application appOwnedBy(Long ownerId) {
        return Application.builder()
                .id(10L)
                .name("my-app")
                .apiKey("app_old")
                .ownerId(ownerId)
                .build();
    }

    @Test
    void createApplication_generatesApiKeyWithPrefix() {
        when(applicationRepository.findByName("my-app")).thenReturn(Optional.empty());
        when(applicationRepository.existsByApiKey(any())).thenReturn(false);
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Application created = applicationService.createApplication(createRequest("my-app"), OWNER_ID);

        assertNotNull(created.getApiKey());
        assertTrue(created.getApiKey().startsWith("app_"), "API key must start with app_ prefix");
        assertEquals(OWNER_ID, created.getOwnerId());
        verify(applicationRepository).save(any(Application.class));
    }

    @Test
    void createApplication_duplicateName_throwsConflict() {
        when(applicationRepository.findByName("my-app")).thenReturn(Optional.of(appOwnedBy(OWNER_ID)));

        assertThrows(IllegalStateException.class,
                () -> applicationService.createApplication(createRequest("my-app"), OWNER_ID));
        verify(applicationRepository, never()).save(any());
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
    void regenerateApiKey_owner_returnsNewPrefixedKey() {
        Application owned = appOwnedBy(OWNER_ID);
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(owned));
        when(applicationRepository.existsByApiKey(any())).thenReturn(false);
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Application updated = applicationService.regenerateApiKey(10L, OWNER_ID);

        assertNotEquals("app_old", updated.getApiKey());
        assertTrue(updated.getApiKey().startsWith("app_"));
    }
}
