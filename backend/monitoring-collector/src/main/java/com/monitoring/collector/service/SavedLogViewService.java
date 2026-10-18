package com.monitoring.collector.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.collector.dto.request.CreateSavedLogViewRequest;
import com.monitoring.collector.dto.request.SavedLogViewQuery;
import com.monitoring.collector.dto.response.SavedLogViewResponse;
import com.monitoring.collector.dto.request.UpdateSavedLogViewRequest;
import com.monitoring.collector.entity.SavedLogView;
import com.monitoring.collector.exception.SavedLogViewAccessDeniedException;
import com.monitoring.collector.exception.SavedLogViewLimitExceededException;
import com.monitoring.collector.exception.SavedLogViewNotFoundException;
import com.monitoring.collector.repository.SavedLogViewRepository;
import com.monitoring.apps.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD for owner-scoped saved log views.
 *
 * <p>Every operation filters by the authenticated {@code organizationId} (the tenant boundary in the
 * current model). The stored query is validated against {@link SavedLogViewQuery} on write, and the
 * referenced application must belong to the same owner — a caller cannot save a view over another
 * tenant's application. The persisted {@code query_params} JSON is treated as data only.
 */
@Service
@RequiredArgsConstructor
public class SavedLogViewService {

    public static final int MAX_SAVED_VIEWS_PER_OWNER = 50;

    private final SavedLogViewRepository savedLogViewRepository;
    private final ApplicationService applicationService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<SavedLogViewResponse> list(Long organizationId) {
        return savedLogViewRepository.findByOrganizationIdOrderByCreatedAtDescIdDesc(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SavedLogViewResponse create(Long organizationId, CreateSavedLogViewRequest request) {
        SavedLogViewQuery query = request.getQueryParams();
        applicationService.getOwnedApplication(query.getApplicationId(), organizationId);

        if (savedLogViewRepository.countByOrganizationId(organizationId) >= MAX_SAVED_VIEWS_PER_OWNER) {
            throw new SavedLogViewLimitExceededException(MAX_SAVED_VIEWS_PER_OWNER);
        }

        SavedLogView saved = savedLogViewRepository.save(SavedLogView.builder()
                .organizationId(organizationId)
                .name(request.getName().trim())
                .queryParams(writeQuery(query))
                .build());

        return toResponse(saved);
    }

    @Transactional
    public SavedLogViewResponse rename(Long organizationId, Long id, UpdateSavedLogViewRequest request) {
        SavedLogView view = getOwnedOrThrow(organizationId, id);
        view.setName(request.getName().trim());
        return toResponse(savedLogViewRepository.save(view));
    }

    @Transactional
    public void delete(Long organizationId, Long id) {
        savedLogViewRepository.delete(getOwnedOrThrow(organizationId, id));
    }

    private SavedLogView getOwnedOrThrow(Long organizationId, Long id) {
        SavedLogView view = savedLogViewRepository.findById(id)
                .orElseThrow(() -> new SavedLogViewNotFoundException(id));
        if (!view.getOrganizationId().equals(organizationId)) {
            throw new SavedLogViewAccessDeniedException();
        }
        return view;
    }

    private String writeQuery(SavedLogViewQuery query) {
        normalize(query);
        try {
            return objectMapper.writeValueAsString(query);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize saved view query", e);
        }
    }

    private void normalize(SavedLogViewQuery query) {
        String searchText = query.getSearchText();
        if (searchText != null) {
            String trimmed = searchText.trim();
            query.setSearchText(trimmed.isEmpty() ? null : trimmed);
        }
    }

    private SavedLogViewResponse toResponse(SavedLogView view) {
        SavedLogViewQuery query;
        try {
            query = objectMapper.readValue(view.getQueryParams(), SavedLogViewQuery.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored saved view query is not valid JSON", e);
        }
        return SavedLogViewResponse.builder()
                .id(view.getId())
                .name(view.getName())
                .queryParams(query)
                .createdAt(view.getCreatedAt())
                .updatedAt(view.getUpdatedAt())
                .build();
    }
}
