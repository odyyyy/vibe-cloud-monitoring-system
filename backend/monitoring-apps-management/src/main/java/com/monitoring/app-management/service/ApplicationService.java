package com.monitoring.storage.service;

import com.monitoring.storage.dto.ApplicationCreateRequest;
import com.monitoring.storage.entity.Application;
import com.monitoring.storage.exception.ApplicationAccessDeniedException;
import com.monitoring.storage.exception.ApplicationNotFoundException;
import com.monitoring.storage.repository.ApplicationRepository;
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
    public Application createApplication(ApplicationCreateRequest request, Long ownerId) {
        if (applicationRepository.findByName(request.getName()).isPresent()) {
            throw new IllegalStateException("An application with this name already exists");
        }

        Application application = Application.builder()
                .name(request.getName())
                .apiKey(generateUniqueApiKey())
                .ownerId(ownerId)
                .build();

        return applicationRepository.save(application);
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

        if (!application.getName().equals(name) && applicationRepository.findByName(name).isPresent()) {
            throw new IllegalStateException("Application name is already taken by another application");
        }

        application.setName(name);
        return applicationRepository.save(application);
    }

    @Transactional
    public Application regenerateApiKey(Long id, Long ownerId) {
        Application application = getOwnedApplication(id, ownerId);
        application.setApiKey(generateUniqueApiKey());
        return applicationRepository.save(application);
    }

    @Transactional
    public void deleteApplication(Long id, Long ownerId) {
        Application application = getOwnedApplication(id, ownerId);
        applicationRepository.delete(application);
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
        } while (applicationRepository.existsByApiKey(apiKey));
        return apiKey;
    }
}
