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
    private static final int API_KEY_HINT_VISIBLE_SUFFIX = 4;

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
                .apiKeyHint(apiKeyHint(rawApiKey))
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

    /** Resolves a read-only query target without allowing access to platform internals. */
    public Application getQueryableApplication(Long id, Long organizationId) {
        return getQueryableApplication(id, organizationId, false);
    }

    /**
     * Resolves an application for a read-only query. Customer applications always require
     * tenant ownership. The platform's internal telemetry application is reachable only when
     * the caller is a platform administrator ({@code allowSystemApplication}).
     */
    public Application getQueryableApplication(Long id, Long organizationId, boolean allowSystemApplication) {
        Application application = findOrThrow(id);
        if (application.isSystem()) {
            if (!allowSystemApplication) {
                throw new ApplicationAccessDeniedException();
            }
        } else {
            verifyOwnership(application, organizationId);
        }
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
        application.setApiKeyHint(apiKeyHint(rawApiKey));
        return toWithKeyResponse(applicationRepository.save(application), rawApiKey);
    }

    @Transactional
    public void deleteApplication(Long id, Long organizationId) {
        Application application = getOwnedApplication(id, organizationId);
        applicationRepository.delete(application);
    }

    private ApplicationWithKeyResponse toWithKeyResponse(Application application, String rawApiKey) {
        return new ApplicationWithKeyResponse(
                application.getId(),
                application.getName(),
                rawApiKey,
                application.getApiKeyHint(),
                application.getCreatedAt());
    }

    /**
     * Builds a short, non-sensitive hint such as {@code app_…1f2e}. Only the public
     * {@code app_} prefix and the last four characters are kept; the rest of the key is
     * never persisted in plaintext.
     */
    static String apiKeyHint(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.length() <= API_KEY_PREFIX.length() + API_KEY_HINT_VISIBLE_SUFFIX) {
            return null;
        }
        return API_KEY_PREFIX + "…" + rawApiKey.substring(rawApiKey.length() - API_KEY_HINT_VISIBLE_SUFFIX);
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
