package com.monitoring.statuspage.controller;

import com.monitoring.auth.web.ClientIpResolver;
import com.monitoring.statuspage.dto.response.PublicStatusPageResponse;
import com.monitoring.statuspage.exception.StatusPageRateLimitedException;
import com.monitoring.statuspage.service.PublicStatusPageRateLimiter;
import com.monitoring.statuspage.service.PublicStatusPageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/public/status-pages")
@RequiredArgsConstructor
public class PublicStatusPageController {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final PublicStatusPageService publicStatusPageService;
    private final PublicStatusPageRateLimiter rateLimiter;
    private final Clock clock;

    @GetMapping("/{slug}")
    public ResponseEntity<PublicStatusPageResponse> get(
            @PathVariable String slug,
            HttpServletRequest request) {
        long retryAfter = rateLimiter.tryAcquire(ClientIpResolver.resolve(request), clock.instant());
        if (retryAfter > 0) {
            throw new StatusPageRateLimitedException(retryAfter);
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE_TTL).cachePublic())
                .body(publicStatusPageService.getBySlug(slug));
    }
}
