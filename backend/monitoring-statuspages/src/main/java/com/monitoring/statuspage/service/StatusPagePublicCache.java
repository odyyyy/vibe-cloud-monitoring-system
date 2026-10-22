package com.monitoring.statuspage.service;

import com.monitoring.statuspage.dto.response.PublicStatusPageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StatusPagePublicCache {

    private final Duration ttl;
    private final int maxEntries;
    private final Map<String, CacheEntry> entries = new ConcurrentHashMap<>();

    public StatusPagePublicCache(
            @Value("${monitoring.status-pages.public.cache-ttl-seconds:30}") long ttlSeconds,
            @Value("${monitoring.status-pages.public.cache-max-entries:1000}") int maxEntries) {
        this.ttl = Duration.ofSeconds(Math.max(0, ttlSeconds));
        this.maxEntries = Math.max(1, maxEntries);
    }

    public Optional<PublicStatusPageResponse> find(String slug, Instant now) {
        if (ttl.isZero()) {
            return Optional.empty();
        }
        CacheEntry entry = entries.get(slug);
        if (entry == null) {
            return Optional.empty();
        }
        if (!now.isBefore(entry.expiresAt())) {
            entries.remove(slug, entry);
            return Optional.empty();
        }
        return Optional.of(entry.response());
    }

    public void put(String slug, PublicStatusPageResponse response, Instant now) {
        if (ttl.isZero()) {
            return;
        }
        if (entries.size() >= maxEntries) {
            evictExpired(now);
            if (entries.size() >= maxEntries) {
                return;
            }
        }
        entries.put(slug, new CacheEntry(response, now.plus(ttl)));
    }

    public void invalidate(String slug) {
        if (slug != null) {
            entries.remove(slug);
        }
    }

    private void evictExpired(Instant now) {
        entries.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private record CacheEntry(PublicStatusPageResponse response, Instant expiresAt) {
    }
}
