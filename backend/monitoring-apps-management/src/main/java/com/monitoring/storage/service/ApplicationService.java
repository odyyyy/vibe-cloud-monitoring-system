package com.monitoring.storage.service;

import com.monitoring.storage.dto.ApplicationCreateRequest;
import com.monitoring.storage.dto.ApplicationWithKeyResponse;
import com.monitoring.storage.entity.Application;
import com.monitoring.storage.exception.ApplicationAccessDeniedException;
import com.monitoring.storage.exception.ApplicationNotFoundException;
import com.monitoring.storage.repository.ApplicationRepository;
import com.monitoring.storage.security.ApiKeyHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

    private static final String API_KEY_PREFIX = "app_";

    private final ApplicationRepository applicationRepository;

    @Transactional
    public ApplicationWithKeyResponse createApplication(ApplicationCreateRequest request, Long ownerId) {
        String name = request.getName().trim();
        if (applicationRepository.existsByNameAndOwnerId(name, ownerId)) {
            throw new IllegalStateException("An application with this name already exists");
        }

        String rawApiKey = generateUniqueApiKey();
        Application application = Application.builder()
                .name(name)
                .apiKeyHash(ApiKeyHasher.hash(rawApiKey))
                .ownerId(ownerId)
                .build();

        return toWithKeyResponse(applicationRepository.save(application), rawApiKey);
    }

    public List<Application> findByOwnerId(Long ownerId) {
        return applicationRepository.findByOwnerId(ownerId);
    }

    public Application getOwnedApplication(Long id, Long ownerId) {
        Application application = findOrThrow(id);
        verifyOwnership(application, ownerId);
        return application;
    }

    @Transactional
    public Application updateApplication(Long id, String name, Long ownerId) {
        Application application = getOwnedApplication(id, ownerId);
        String newName = name.trim();

        if (!application.getName().equals(newName)
                && applicationRepository.existsByNameAndOwnerId(newName, ownerId)) {
            throw new IllegalStateException("Application name is already taken by another application");
        }

        application.setName(newName);
        return applicationRepository.save(application);
    }

    @Transactional
    public ApplicationWithKeyResponse regenerateApiKey(Long id, Long ownerId) {
        Application application = getOwnedApplication(id, ownerId);
        String rawApiKey = generateUniqueApiKey();
        application.setApiKeyHash(ApiKeyHasher.hash(rawApiKey));
        return toWithKeyResponse(applicationRepository.save(application), rawApiKey);
    }

    @Transactional
    public void deleteApplication(Long id, Long ownerId) {
        Application application = getOwnedApplication(id, ownerId);
        applicationRepository.delete(application);
    }

    private ApplicationWithKeyResponse toWithKeyResponse(Application application, String rawApiKey) {
        return new ApplicationWithKeyResponse(
                application.getId(), application.getName(), rawApiKey, application.getCreatedAt());
    }

    private Application findOrThrow(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));
    }

    private void verifyOwnership(Application application, Long ownerId) {
        if (!application.getOwnerId().equals(ownerId)) {
            throw new ApplicationAccessDeniedException();
        }
    }

    private String generateUniqueApiKey() {
        String apiKey;
        do {
            apiKey = API_KEY_PREFIX + UUID.randomUUID();
        } while (applicationRepository.existsByApiKeyHash(ApiKeyHasher.hash(apiKey)));
        return apiKey;
    }
}
