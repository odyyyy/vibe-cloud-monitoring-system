package com.monitoring.statuspage.service;

import com.monitoring.statuspage.dto.response.PublicStatusPageResponse;
import com.monitoring.statuspage.exception.StatusPageNotFoundException;
import com.monitoring.statuspage.repository.StatusPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PublicStatusPageService {

    private final StatusPageRepository statusPageRepository;
    private final StatusPageStatusService statusService;
    private final StatusPagePublicCache publicCache;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PublicStatusPageResponse getBySlug(String slug) {
        Instant now = clock.instant();
        Optional<PublicStatusPageResponse> cached = publicCache.find(slug, now);
        if (cached.isPresent()) {
            return cached.get();
        }

        PublicStatusPageResponse response = statusPageRepository
                .findActiveWithComponentsBySlug(slug)
                .map(page -> statusService.toPublicResponse(page, now))
                .orElseThrow(StatusPageNotFoundException::new);

        publicCache.put(slug, response, now);
        return response;
    }
}
