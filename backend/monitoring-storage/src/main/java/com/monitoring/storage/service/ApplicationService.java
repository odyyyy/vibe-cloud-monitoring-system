package com.monitoring.storage.service;

import com.monitoring.storage.dto.ApplicationCreateRequest;
import com.monitoring.storage.dto.ApplicationUpdateRequest;
import com.monitoring.storage.entity.Application;
import com.monitoring.storage.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

    private final ApplicationRepository applicationRepository;

    @Transactional
    public Application createApplication(@Valid ApplicationCreateRequest request, Long ownerId) {
        // Проверяем уникальность названия приложения (опционально)
        if (!applicationRepository.findByName(request.getName()).isEmpty()) {
            throw new IllegalStateException("An application with this name already exists");
        }

        String apiKey = generateUniqueApiKey();

        Application application = Application.builder()
                .name(request.getName())
                .apiKey(apiKey)
                .ownerId(ownerId)
                .build();

        return applicationRepository.save(application);
    }

    @Transactional
    public Application regenerateApiKey(Long appId) {
        Application application = applicationRepository.findById(appId)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        // Проверяем права владельца
        Long ownerId = getOwnerId();
        if (!application.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("Access denied. You are not the owner of this application.");
        }

        String newApiKey = generateUniqueApiKey();
        application.setApiKey(newApiKey);

        try {
            return applicationRepository.save(application);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // В случае редкой ситуации с конфликтом API-ключа, генерируем ещё раз
            throw new IllegalStateException("Failed to regenerate API key due to uniqueness constraint", e);
        }
    }

    @Transactional
    public Application updateApplication(Long appId, String name) {
        Application application = applicationRepository.findById(appId)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        // Проверяем права владельца
        Long ownerId = getOwnerId();
        if (!application.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("Access denied. You are not the owner of this application.");
        }

        // Проверяем, не используется ли имя другим приложением
        String currentName = application.getName();
        if (!currentName.equals(name) && 
            !applicationRepository.findByName(name).stream()
                .noneMatch(app -> !app.getId().equals(application.getId()))) {
            throw new IllegalStateException("Application name is already taken by another application");
        }

        application.setName(name);

        return applicationRepository.save(application);
    }

    public Optional<Application> findById(Long id) {
        return applicationRepository.findById(id);
    }

    public List<Application> findByOwnerId(Long ownerId) {
        return applicationRepository.findByOwnerId(ownerId);
    }

    @Transactional
    public void deleteById(Long id) {
        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        // Проверяем права владельца
        Long ownerId = getOwnerId();
        if (!application.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("Access denied. You are not the owner of this application.");
        }

        applicationRepository.deleteById(id);
    }

    private Long getOwnerId() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        Object principal = securityContext.getAuthentication().getPrincipal();
        
        if (principal instanceof String username) {
            // Если username - это userId, используем его напрямую
            try {
                return Long.parseLong(username);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Invalid user ID format. Expected numeric ID.");
            }
        } else {
            // Если principal не String, пробуем получить через дополнительную логику
            throw new RuntimeException("Authentication not available or invalid principal type: " + 
                                   principal.getClass().getName());
        }
    }

    private String generateUniqueApiKey() {
        String apiKey;
        do {
            apiKey = UUID.randomUUID().toString();
        } while (applicationRepository.existsByApiKey(apiKey));
        return apiKey;
    }
}
