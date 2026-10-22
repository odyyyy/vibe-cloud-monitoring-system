package com.monitoring.statuspage.service;

import com.monitoring.healthcheck.repository.HealthCheckConfigRepository;
import com.monitoring.statuspage.dto.request.CreateStatusPageRequest;
import com.monitoring.statuspage.dto.request.StatusPageComponentRequest;
import com.monitoring.statuspage.dto.request.UpdateStatusPageRequest;
import com.monitoring.statuspage.dto.response.StatusPageResponse;
import com.monitoring.statuspage.entity.StatusPage;
import com.monitoring.statuspage.entity.StatusPageComponent;
import com.monitoring.statuspage.exception.InvalidStatusPageException;
import com.monitoring.statuspage.exception.StatusPageAccessDeniedException;
import com.monitoring.statuspage.exception.StatusPageLimitExceededException;
import com.monitoring.statuspage.exception.StatusPageNotFoundException;
import com.monitoring.statuspage.exception.StatusPageSlugConflictException;
import com.monitoring.statuspage.repository.StatusPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class StatusPageService {

    public static final int MAX_STATUS_PAGES_PER_ORGANIZATION = 3;
    public static final int MAX_COMPONENTS_PER_PAGE = 20;

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final int MIN_SLUG_LENGTH = 3;
    private static final int MAX_SLUG_LENGTH = 50;
    private static final Set<String> RESERVED_SLUGS = Set.of(
            "api", "admin", "app", "s", "login", "register", "static", "health", "status");

    private final StatusPageRepository statusPageRepository;
    private final HealthCheckConfigRepository healthCheckConfigRepository;
    private final StatusPagePublicCache publicCache;

    @Transactional(readOnly = true)
    public List<StatusPageResponse> list(Long organizationId) {
        return statusPageRepository.findAllWithComponentsByOrganizationId(organizationId).stream()
                .map(StatusPageResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public StatusPageResponse get(Long organizationId, Long id) {
        return StatusPageResponse.fromEntity(findOwned(organizationId, id));
    }

    @Transactional
    public StatusPageResponse create(Long organizationId, CreateStatusPageRequest request) {
        if (statusPageRepository.countByOrganizationId(organizationId) >= MAX_STATUS_PAGES_PER_ORGANIZATION) {
            throw new StatusPageLimitExceededException(MAX_STATUS_PAGES_PER_ORGANIZATION);
        }

        String slug = validateSlug(request.getSlug());
        if (statusPageRepository.existsBySlug(slug)) {
            throw new StatusPageSlugConflictException();
        }

        StatusPage page = StatusPage.builder()
                .organizationId(organizationId)
                .slug(slug)
                .title(request.getTitle().trim())
                .description(normalizeDescription(request.getDescription()))
                .active(Boolean.TRUE.equals(request.getActive()))
                .build();
        applyComponents(page, request.getComponents(), organizationId);

        return StatusPageResponse.fromEntity(statusPageRepository.save(page));
    }

    @Transactional
    public StatusPageResponse update(Long organizationId, Long id, UpdateStatusPageRequest request) {
        StatusPage page = findOwned(organizationId, id);
        String previousSlug = page.getSlug();
        String slug = validateSlug(request.getSlug());
        if (statusPageRepository.existsBySlugAndIdNot(slug, id)) {
            throw new StatusPageSlugConflictException();
        }

        page.setSlug(slug);
        page.setTitle(request.getTitle().trim());
        page.setDescription(normalizeDescription(request.getDescription()));
        if (request.getActive() != null) {
            page.setActive(request.getActive());
        }
        applyComponents(page, request.getComponents(), organizationId);

        StatusPageResponse response = StatusPageResponse.fromEntity(statusPageRepository.save(page));
        publicCache.invalidate(previousSlug);
        publicCache.invalidate(slug);
        return response;
    }

    @Transactional
    public void delete(Long organizationId, Long id) {
        StatusPage page = findOwned(organizationId, id);
        String slug = page.getSlug();
        statusPageRepository.delete(page);
        publicCache.invalidate(slug);
    }

    private StatusPage findOwned(Long organizationId, Long id) {
        StatusPage page = statusPageRepository.findWithComponentsById(id)
                .orElseThrow(StatusPageNotFoundException::new);
        if (!page.getOrganizationId().equals(organizationId)) {
            throw new StatusPageAccessDeniedException();
        }
        return page;
    }

    private void applyComponents(StatusPage page,
                                 List<StatusPageComponentRequest> requests,
                                 Long organizationId) {
        List<StatusPageComponentRequest> components = requests == null ? List.of() : requests;
        if (components.size() > MAX_COMPONENTS_PER_PAGE) {
            throw new InvalidStatusPageException(
                    "A status page cannot have more than " + MAX_COMPONENTS_PER_PAGE + " components");
        }

        page.getComponents().clear();
        int sortOrder = 0;
        for (StatusPageComponentRequest request : components) {
            String name = request.getName() == null ? "" : request.getName().trim();
            if (name.isEmpty()) {
                throw new InvalidStatusPageException("Component name must not be blank");
            }
            validateLinkedHealthCheck(organizationId, request.getLinkedHealthCheckId());
            page.getComponents().add(StatusPageComponent.builder()
                    .statusPage(page)
                    .name(name)
                    .sortOrder(sortOrder++)
                    .linkedHealthCheckId(request.getLinkedHealthCheckId())
                    .build());
        }
    }

    private void validateLinkedHealthCheck(Long organizationId, Long healthCheckId) {
        if (healthCheckId == null) {
            return;
        }
        if (healthCheckConfigRepository.findByIdAndOrganizationId(healthCheckId, organizationId).isEmpty()) {
            throw new InvalidStatusPageException(
                    "linkedHealthCheckId " + healthCheckId + " is not valid for this organization");
        }
    }

    private String validateSlug(String rawSlug) {
        String slug = rawSlug == null ? "" : rawSlug.trim();
        if (slug.length() < MIN_SLUG_LENGTH || slug.length() > MAX_SLUG_LENGTH
                || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new InvalidStatusPageException(
                    "Slug must be 3-50 characters of lowercase letters, digits and single hyphens");
        }
        if (RESERVED_SLUGS.contains(slug)) {
            throw new InvalidStatusPageException("Slug '" + slug + "' is reserved");
        }
        return slug;
    }

    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
