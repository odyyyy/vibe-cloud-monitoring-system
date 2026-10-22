package com.monitoring.revisions.service;

import com.monitoring.auth.entity.User;
import com.monitoring.auth.repository.UserRepository;
import com.monitoring.common.dto.response.PageResponse;
import com.monitoring.organization.tenant.CurrentTenantService;
import com.monitoring.revisions.dto.ConfigRevisionResponse;
import com.monitoring.revisions.dto.ConfigRevisionSummaryResponse;
import com.monitoring.revisions.entity.ConfigChangeType;
import com.monitoring.revisions.entity.ConfigResourceType;
import com.monitoring.revisions.entity.ConfigRevision;
import com.monitoring.revisions.exception.ConfigRevisionNotFoundException;
import com.monitoring.revisions.repository.ConfigRevisionCounterRepository;
import com.monitoring.revisions.repository.ConfigRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Stores and reads the lightweight configuration history of dashboards and alert rules.
 *
 * <p>Writes join the caller's transaction, so a revision is committed only together with the
 * resource change it describes. A failed resource write therefore never leaves a revision
 * behind, and a successful write is never missing its history entry.
 *
 * <p>History is best-effort: a snapshot above {@link #MAX_SNAPSHOT_BYTES} is skipped with a
 * warning rather than failing the user's save, and an unchanged snapshot is not recorded.
 *
 * <p>Revision numbers are handed out by the counter of the resource (see
 * {@link ConfigRevisionCounterRepository}). The counter is incremented with a single atomic
 * statement, so two concurrent saves of the same dashboard or alert rule cannot allocate the
 * same revision number, and the row lock of the counter serializes the writers of a resource.
 */
@Service
@RequiredArgsConstructor
public class ConfigRevisionService {

    private static final Logger log = LoggerFactory.getLogger(ConfigRevisionService.class);

    /** Number of revisions retained per resource, newest first. */
    public static final int MAX_REVISIONS_PER_RESOURCE = 10;

    /** Snapshots above this size are not recorded; the resource save still succeeds. */
    public static final int MAX_SNAPSHOT_BYTES = 256 * 1024;

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    /** Attempts to allocate a revision number before the request fails. */
    private static final int ALLOCATION_ATTEMPTS = 3;

    private final ConfigRevisionRepository revisionRepository;
    private final ConfigRevisionCounterRepository counterRepository;
    private final UserRepository userRepository;
    private final CurrentTenantService currentTenantService;

    /** Records revision 1 of a newly created resource. */
    @Transactional
    public void recordCreate(Long organizationId, ConfigResourceType resourceType, Long resourceId,
                             int schemaVersion, String snapshot) {
        if (tooLarge(snapshot)) {
            return;
        }
        // The counter of a new resource is created here, so that later saves of the same
        // resource only have to increment it.
        createCounter(organizationId, resourceType, resourceId);
        int number = nextRevisionNumber(organizationId, resourceType, resourceId);
        storeRevision(organizationId, resourceType, resourceId, number, currentUserIdOrNull(),
                ConfigChangeType.CREATE, null, schemaVersion, snapshot);
        prune(organizationId, resourceType, resourceId, number);
    }

    /**
     * Records a post-change snapshot. The first update of a resource that predates versioning
     * first stores its pre-change state as a {@link ConfigChangeType#BASELINE} revision.
     * No-op saves are ignored.
     */
    @Transactional
    public void recordUpdate(Long organizationId, ConfigResourceType resourceType, Long resourceId,
                             int schemaVersion, String beforeSnapshot, String afterSnapshot) {
        if (tooLarge(afterSnapshot) || Objects.equals(beforeSnapshot, afterSnapshot)) {
            return;
        }

        Optional<ConfigRevision> latest = latestRevision(organizationId, resourceType, resourceId);
        if (latest.isPresent() && Objects.equals(latest.get().getSnapshot(), afterSnapshot)) {
            return;
        }

        int number = nextRevisionNumber(organizationId, resourceType, resourceId);
        if (latest.isEmpty() && !tooLarge(beforeSnapshot)) {
            storeRevision(organizationId, resourceType, resourceId, number, null,
                    ConfigChangeType.BASELINE, null, schemaVersion, beforeSnapshot);
            number = nextRevisionNumber(organizationId, resourceType, resourceId);
        }
        storeRevision(organizationId, resourceType, resourceId, number, currentUserIdOrNull(),
                ConfigChangeType.UPDATE, null, schemaVersion, afterSnapshot);
        prune(organizationId, resourceType, resourceId, number);
    }

    /** Records a rollback, always as a new entry even when its content matches an earlier state. */
    @Transactional
    public void recordRestore(Long organizationId, ConfigResourceType resourceType, Long resourceId,
                              int restoredFromRevisionNumber, int schemaVersion, String snapshot) {
        if (tooLarge(snapshot)) {
            return;
        }
        int number = nextRevisionNumber(organizationId, resourceType, resourceId);
        storeRevision(organizationId, resourceType, resourceId, number, currentUserIdOrNull(),
                ConfigChangeType.RESTORE, restoredFromRevisionNumber, schemaVersion, snapshot);
        prune(organizationId, resourceType, resourceId, number);
    }

    @Transactional(readOnly = true)
    public PageResponse<ConfigRevisionSummaryResponse> list(Long organizationId,
                                                            ConfigResourceType resourceType,
                                                            Long resourceId,
                                                            Integer page,
                                                            Integer size) {
        int effectivePage = page == null ? 0 : Math.max(page, 0);
        int effectiveSize = size == null
                ? DEFAULT_PAGE_SIZE
                : Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Pageable pageable = PageRequest.of(
                effectivePage, effectiveSize, Sort.by(Sort.Order.desc("revisionNumber")));

        Page<ConfigRevision> revisions =
                revisionRepository.findByOrganizationIdAndResourceTypeAndResourceId(
                        organizationId, resourceType, resourceId, pageable);
        Map<Long, String> names = userNames(revisions.getContent());
        return PageResponse.of(revisions, revision -> ConfigRevisionSummaryResponse.from(
                revision, revision.getUserId() == null ? null : names.get(revision.getUserId())));
    }

    @Transactional(readOnly = true)
    public ConfigRevisionResponse get(Long organizationId, ConfigResourceType resourceType,
                                      Long resourceId, int revisionNumber) {
        ConfigRevision revision = requireRevision(organizationId, resourceType, resourceId, revisionNumber);
        return ConfigRevisionResponse.from(revision, resolveUserName(revision.getUserId()));
    }

    /** Loads the entity for a restore; the caller validates and applies its snapshot. */
    @Transactional(readOnly = true)
    public ConfigRevision requireRevision(Long organizationId, ConfigResourceType resourceType,
                                          Long resourceId, int revisionNumber) {
        return revisionRepository
                .findFirstByOrganizationIdAndResourceTypeAndResourceIdAndRevisionNumberOrderByIdDesc(
                        organizationId, resourceType, resourceId, revisionNumber)
                .orElseThrow(() -> new ConfigRevisionNotFoundException(revisionNumber));
    }

    /** Deletes the history of a resource, in the same transaction as the resource deletion. */
    @Transactional
    public void deleteAll(Long organizationId, ConfigResourceType resourceType, Long resourceId) {
        revisionRepository.deleteResourceHistory(organizationId, resourceType, resourceId);
        counterRepository.deleteCounter(organizationId, resourceType, resourceId);
    }

    /**
     * Hands out the next revision number of a resource.
     *
     * <p>The counter is created on demand. A concurrent writer may create it first, in which
     * case the insert fails with a duplicate key and the counter is used instead.
     */
    private int nextRevisionNumber(Long organizationId, ConfigResourceType resourceType, Long resourceId) {
        for (int attempt = 0; attempt < ALLOCATION_ATTEMPTS; attempt++) {
            if (counterRepository.increment(organizationId, resourceType, resourceId) > 0) {
                return allocatedNumber(organizationId, resourceType, resourceId);
            }
            createCounter(organizationId, resourceType, resourceId);
            // A resource that predates the counters continues after its newest revision.
            Integer newest = revisionRepository.findMaxRevisionNumber(organizationId, resourceType, resourceId);
            if (newest != null && newest > 0) {
                counterRepository.raiseTo(organizationId, resourceType, resourceId, newest);
            }
            if (counterRepository.increment(organizationId, resourceType, resourceId) > 0) {
                return allocatedNumber(organizationId, resourceType, resourceId);
            }
        }
        throw new IllegalStateException(
                "Could not allocate a revision number for " + resourceType + " " + resourceId);
    }

    /** Creates the counter of a resource, if it does not exist yet. */
    private void createCounter(Long organizationId, ConfigResourceType resourceType, Long resourceId) {
        try {
            counterRepository.insertCounter(organizationId, resourceType.name(), resourceId);
        } catch (DataIntegrityViolationException ex) {
            // Another writer created the counter first.
            log.debug("Revision counter of {} {} was created concurrently", resourceType, resourceId);
        }
    }

    private int allocatedNumber(Long organizationId, ConfigResourceType resourceType, Long resourceId) {
        Integer number = counterRepository.findLastNumber(organizationId, resourceType, resourceId);
        if (number == null) {
            throw new IllegalStateException(
                    "Revision counter of " + resourceType + " " + resourceId + " is missing");
        }
        return number;
    }

    private Optional<ConfigRevision> latestRevision(Long organizationId, ConfigResourceType resourceType,
                                                    Long resourceId) {
        return revisionRepository
                .findTopByOrganizationIdAndResourceTypeAndResourceIdOrderByRevisionNumberDescIdDesc(
                        organizationId, resourceType, resourceId);
    }

    private void storeRevision(Long organizationId, ConfigResourceType resourceType, Long resourceId,
                               int revisionNumber, Long userId, ConfigChangeType changeType,
                               Integer restoredFrom, int schemaVersion, String snapshot) {
        revisionRepository.save(ConfigRevision.builder()
                .organizationId(organizationId)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .revisionNumber(revisionNumber)
                .userId(userId)
                .changeType(changeType)
                .restoredFromRevisionNumber(restoredFrom)
                .schemaVersion(schemaVersion)
                .snapshot(snapshot)
                .build());
    }

    /**
     * Keeps the newest {@link #MAX_REVISIONS_PER_RESOURCE} revisions of a resource. Older rows
     * are removed with a single statement, so a concurrent writer cannot fail the request by
     * deleting a row that another transaction has already removed.
     */
    private void prune(Long organizationId, ConfigResourceType resourceType, Long resourceId, int lastNumber) {
        int obsoleteUpTo = lastNumber - MAX_REVISIONS_PER_RESOURCE;
        if (obsoleteUpTo < 1) {
            return;
        }
        revisionRepository.deleteResourceHistoryUpTo(organizationId, resourceType, resourceId, obsoleteUpTo);
    }

    private boolean tooLarge(String snapshot) {
        if (snapshot == null) {
            return false;
        }
        int bytes = snapshot.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_SNAPSHOT_BYTES) {
            log.warn("Skipping configuration revision: snapshot is {} bytes (limit {})",
                    bytes, MAX_SNAPSHOT_BYTES);
            return true;
        }
        return false;
    }

    private Long currentUserIdOrNull() {
        try {
            return currentTenantService.requireUserId();
        } catch (RuntimeException ex) {
            // Background/direct service invocations may not carry a security context.
            return null;
        }
    }

    private Map<Long, String> userNames(List<ConfigRevision> revisions) {
        List<Long> userIds = revisions.stream()
                .map(ConfigRevision::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (first, second) -> first));
    }

    private String resolveUserName(Long userId) {
        return userId == null ? null : userRepository.findById(userId)
                .map(User::getUsername)
                .orElse(null);
    }
}
