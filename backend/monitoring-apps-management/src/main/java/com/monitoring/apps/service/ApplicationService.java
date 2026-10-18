package com.monitoring.apps.service;

import com.monitoring.apps.dto.request.ApplicationCreateRequest;
import com.monitoring.apps.dto.response.ApplicationWithKeyResponse;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.exception.ApplicationAccessDeniedException;
import com.monitoring.apps.exception.ApplicationNameConflictException;
import com.monitoring.apps.exception.ApplicationNotFoundException;
import com.monitoring.apps.repository.ApplicationRepository;
import com.monitoring.apps.security.ApiKeyHasher;
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
    public ApplicationWithKeyResponse createApplication(ApplicationCreateRequest request, Long organizationId) {
        String name = request.getName().trim();
        if (applicationRepository.existsByNameAndOrganizationId(name, organizationId)) {
            throw new ApplicationNameConflictException(name);
        }

        String rawApiKey = generateUniqueApiKey();
        Application application = Application.builder()
                .name(name)
                .apiKeyHash(ApiKeyHasher.hash(rawApiKey))
                .organizationId(organizationId)
                .build();

        return toWithKeyResponse(applicationRepository.save(application), rawApiKey);
    }

    public List<Application> findByOrganizationId(Long organizationId) {
        return applicationRepository.findByOrganizationId(organizationId);
    }

    public Application getOwnedApplication(Long id, Long organizationId) {
        Application application = findOrThrow(id);
        verifyOwnership(application, organizationId);
        return application;
    }

    @Transactional
    public Application updateApplication(Long id, String name, Long organizationId) {
        Application application = getOwnedApplication(id, organizationId);
        String newName = name.trim();

        if (!application.getName().equals(newName)
                && applicationRepository.existsByNameAndOrganizationId(newName, organizationId)) {
            throw new ApplicationNameConflictException(newName);
        }

        application.setName(newName);
        return applicationRepository.save(application);
    }

    @Transactional
    public ApplicationWithKeyResponse regenerateApiKey(Long id, Long organizationId) {
        Application application = getOwnedApplication(id, organizationId);
        String rawApiKey = generateUniqueApiKey();
        application.setApiKeyHash(ApiKeyHasher.hash(rawApiKey));
        return toWithKeyResponse(applicationRepository.save(application), rawApiKey);
    }

    @Transactional
    public void deleteApplication(Long id, Long organizationId) {
        Application application = getOwnedApplication(id, organizationId);
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

    private void verifyOwnership(Application application, Long organizationId) {
        if (!application.getOrganizationId().equals(organizationId)) {
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
