package com.monitoring.api.controller;

import com.monitoring.storage.dto.ApplicationResponse;
import com.monitoring.storage.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    public ResponseEntity<List<ApplicationResponse>> listApplications() {
        try {
            // Получаем ownerId из SecurityContext
            Long ownerId = getOwnerId();
            
            // Возвращаем только приложения текущего пользователя
            List<Application> applications = applicationService.findByOwnerId(ownerId);
            
            return ResponseEntity.ok(
                applications.stream()
                    .map(ApplicationResponse::fromEntity)
                    .toList()
            );
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            throw e;
        }
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> createApplication(
            @Valid @RequestBody ApplicationCreateRequest request) {
        
        Long ownerId = getOwnerId();
        
        Application application = applicationService.createApplication(
                request.getName(), 
                ownerId
        );

        return ResponseEntity.status(HttpStatus.CREATED).build()
                .header("Location", "/api/applications/" + application.getId())
                .body(ApplicationResponse.fromEntity(application));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getApplication(@PathVariable Long id) {
        try {
            Application application = applicationService.findById(id)
                .orElseThrow(() -> new RuntimeException("Application not found"));
            
            return ResponseEntity.ok(ApplicationResponse.fromEntity(application));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            throw e;
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApplicationResponse> updateApplication(
            @PathVariable Long id, 
            @Valid @RequestBody ApplicationUpdateRequest request) {
        
        try {
            Application application = applicationService.updateApplication(id, request.getName());
            
            return ResponseEntity.ok(ApplicationResponse.fromEntity(application));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            throw e;
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(@PathVariable Long id) {
        try {
            applicationService.deleteById(id);
            
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            if (e.getMessage() != null && e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            throw e;
        }
    }

    @PostMapping("/{id}/regenerate-api-key")
    public ResponseEntity<ApplicationResponse> regenerateApiKey(
            @PathVariable Long id) {
        
        try {
            Application application = applicationService.regenerateApiKey(id);
            
            return ResponseEntity.ok(ApplicationResponse.fromEntity(application));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            if (e.getMessage() != null && e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            throw e;
        }
    }

    private Long getOwnerId() {
        var securityContext = org.springframework.security.core.context.SecurityContextHolder.getContext();
        Object principal = securityContext.getAuthentication().getPrincipal();
        
        if (principal instanceof String username) {
            // Если username - это userId, используем его напрямую
            try {
                return Long.parseLong(username);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Invalid user ID format. Expected numeric ID.");
            }
        } else if (principal instanceof org.springframework.security.core.userdetails.User user) {
            // Если это User, проверяем имя пользователя на совпадение с userId
            // В реальном приложении лучше хранить userId в JWT токене
            throw new RuntimeException("User authentication found. Use numeric username or add userId to JWT token.");
        } else {
            throw new RuntimeException("Authentication not available: " + 
                                   principal.getClass().getName());
        }
    }
}
