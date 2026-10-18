package com.monitoring.audit.service;

import com.monitoring.audit.dto.request.AuditEventQuery;
import com.monitoring.audit.dto.response.AuditEventResponse;
import com.monitoring.common.dto.PageResponse;
import com.monitoring.audit.entity.AuditEvent;
import com.monitoring.audit.repository.AuditEventRepository;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Writes and reads the audit trail.
 *
 * <p>Writes run in an independent transaction ({@link Propagation#REQUIRES_NEW}) so a recorded
 * event is committed as soon as the action completes and is not rolled back together with
 * unrelated work. Reads are always scoped to the acting organization; the identifier comes
 * from the authenticated tenant context, never from the request. The acting user is stored
 * separately for display.
 */
@Service
@RequiredArgsConstructor
public class AuditEventService {

    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;

    /** Persists a single audit event synchronously. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long organizationId, Long userId, String action, String resourceId, String ipAddress) {
        auditEventRepository.save(AuditEvent.builder()
                .organizationId(organizationId)
                .userId(userId)
                .action(action)
                .resourceId(resourceId)
                .ipAddress(ipAddress)
                .timestamp(Instant.now())
                .build());
    }

    /** Returns the organization's events, newest first, with optional filters. */
    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> list(Long organizationId, AuditEventQuery query) {
        Pageable pageable = PageRequest.of(
                query.getPage(),
                query.getSize(),
                Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id"))
        );

        Page<AuditEvent> page = auditEventRepository.findAll(toSpecification(organizationId, query), pageable);
        Map<Long, String> usernames = resolveUsernames(page.getContent());

        return PageResponse.of(page, event -> AuditEventResponse.from(event, usernames.get(event.getUserId())));
    }

    /** Returns a single event only when it belongs to the given organization; otherwise empty. */
    @Transactional(readOnly = true)
    public Optional<AuditEventResponse> find(Long organizationId, Long eventId) {
        return auditEventRepository.findById(eventId)
                .filter(event -> event.getOrganizationId().equals(organizationId))
                .map(event -> AuditEventResponse.from(event, resolveUsername(event.getUserId())));
    }

    private Specification<AuditEvent> toSpecification(Long organizationId, AuditEventQuery query) {
        String action = normalize(query.getAction());

        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), organizationId));

            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (query.getFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), query.getFrom()));
            }
            if (query.getTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), query.getTo()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Map<Long, String> resolveUsernames(List<AuditEvent> events) {
        List<Long> userIds = events.stream()
                .map(AuditEvent::getUserId)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (first, second) -> first));
    }

    private String resolveUsername(Long userId) {
        return userRepository.findById(userId).map(User::getUsername).orElse(null);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
