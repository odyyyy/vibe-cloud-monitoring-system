package com.monitoring.alerts.service;

import com.monitoring.alerts.dto.request.AddIncidentCommentRequest;
import com.monitoring.alerts.dto.response.IncidentCommentResponse;
import com.monitoring.alerts.entity.AlertIncident;
import com.monitoring.alerts.entity.IncidentComment;
import com.monitoring.alerts.exception.AlertIncidentAccessDeniedException;
import com.monitoring.alerts.exception.AlertIncidentNotFoundException;
import com.monitoring.alerts.exception.IncidentCommentRateLimitedException;
import com.monitoring.alerts.repository.AlertIncidentRepository;
import com.monitoring.alerts.repository.IncidentCommentRepository;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.repository.UserRepository;
import com.monitoring.common.dto.response.PageResponse;
import com.monitoring.organization.entity.Membership;
import com.monitoring.organization.entity.MembershipRole;
import com.monitoring.organization.exception.TenantAccessDeniedException;
import com.monitoring.organization.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Incident war-room threads: tenant-scoped comments with slash-command lifecycle actions.
 *
 * <p>Every read is scoped by the incident organization, and posting first verifies that the
 * incident belongs to the caller's tenant. A slash command is only honored for roles that may
 * change incidents ({@code ADMIN}/{@code OWNER}); a viewer's message is rejected rather than
 * silently downgraded to text.
 */
@Service
@RequiredArgsConstructor
public class IncidentCommentService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;

    private static final Pattern ACKNOWLEDGE_COMMAND =
            Pattern.compile("(?i)(?:^|\\s)/acknowledge\\b");
    private static final Pattern RESOLVE_COMMAND =
            Pattern.compile("(?i)(?:^|\\s)/resolve\\b");

    private final IncidentCommentRepository commentRepository;
    private final AlertIncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final AlertService alertService;
    private final IncidentCommentRateLimiter rateLimiter;

    /** Newest-first page of comments for one incident. */
    @Transactional(readOnly = true)
    public PageResponse<IncidentCommentResponse> listComments(
            Long organizationId, Long incidentId, Integer page, Integer size) {
        requireIncident(organizationId, incidentId);

        int effectivePage = page == null ? 0 : Math.max(0, page);
        int effectiveSize = size == null
                ? DEFAULT_PAGE_SIZE
                : Math.max(1, Math.min(size, MAX_PAGE_SIZE));

        Page<IncidentComment> comments = commentRepository
                .findByOrganizationIdAndIncidentIdOrderByCreatedAtDescIdDesc(
                        organizationId,
                        incidentId,
                        PageRequest.of(effectivePage, effectiveSize));

        Map<Long, Author> authors = resolveAuthors(organizationId, comments.getContent());
        return PageResponse.of(comments, comment -> toResponse(comment, authors));
    }

    /**
     * Stores a comment and, when it contains {@code /acknowledge} or {@code /resolve}, applies
     * the corresponding lifecycle transition and appends a system confirmation. All writes share
     * one transaction, so a rejected transition leaves the thread unchanged.
     */
    @Transactional
    public IncidentCommentResponse addComment(
            Long organizationId,
            Long userId,
            MembershipRole role,
            Long incidentId,
            AddIncidentCommentRequest request) {
        AlertIncident incident = requireIncident(organizationId, incidentId);

        long retryAfter = rateLimiter.tryAcquire(userId, Instant.now());
        if (retryAfter > 0) {
            throw new IncidentCommentRateLimitedException(retryAfter);
        }

        String text = IncidentCommentSanitizer.sanitize(request == null ? null : request.getText());
        boolean acknowledge = ACKNOWLEDGE_COMMAND.matcher(text).find();
        boolean resolve = RESOLVE_COMMAND.matcher(text).find();
        if ((acknowledge || resolve) && !canManageIncidents(role)) {
            throw new TenantAccessDeniedException(
                    "Only administrators can change incident state from a comment");
        }

        String username = username(userId);
        IncidentComment comment = commentRepository.save(IncidentComment.builder()
                .incidentId(incidentId)
                .organizationId(organizationId)
                .userId(userId)
                .text(text)
                .system(false)
                .build());

        if (acknowledge) {
            alertService.acknowledgeIncident(organizationId, userId, incidentId);
            saveSystemComment(incident, userId, username + " acknowledged this incident.");
        }
        if (resolve) {
            alertService.resolveIncident(organizationId, userId, incidentId);
            saveSystemComment(incident, userId, username + " resolved this incident.");
        }

        return IncidentCommentResponse.fromEntity(comment, username, role);
    }

    private void saveSystemComment(
            AlertIncident incident, Long userId, String text) {
        commentRepository.save(IncidentComment.builder()
                .incidentId(incident.getId())
                .organizationId(incident.getOrganizationId())
                .userId(userId)
                .text(IncidentCommentSanitizer.sanitize(text))
                .system(true)
                .build());
    }

    private boolean canManageIncidents(MembershipRole role) {
        return role == MembershipRole.ADMIN || role == MembershipRole.OWNER;
    }

    private AlertIncident requireIncident(Long organizationId, Long incidentId) {
        AlertIncident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new AlertIncidentNotFoundException(incidentId));
        if (!Objects.equals(incident.getOrganizationId(), organizationId)) {
            throw new AlertIncidentAccessDeniedException();
        }
        return incident;
    }

    private String username(Long userId) {
        if (userId == null) {
            return "A user";
        }
        return userRepository.findById(userId)
                .map(User::getUsername)
                .orElse("User #" + userId);
    }

    private Map<Long, Author> resolveAuthors(
            Long organizationId, List<IncidentComment> comments) {
        Set<Long> userIds = comments.stream()
                .map(IncidentComment::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> usernames = new HashMap<>();
        for (User user : userRepository.findAllById(userIds)) {
            usernames.put(user.getId(), user.getUsername());
        }

        Map<Long, MembershipRole> roles = new HashMap<>();
        for (Membership membership : membershipRepository
                .findByOrganizationIdAndUserIdIn(organizationId, userIds)) {
            roles.put(membership.getUserId(), membership.getRole());
        }

        Map<Long, Author> authors = new HashMap<>();
        for (Long userId : userIds) {
            authors.put(userId, new Author(usernames.get(userId), roles.get(userId)));
        }
        return authors;
    }

    private IncidentCommentResponse toResponse(
            IncidentComment comment, Map<Long, Author> authors) {
        Author author = comment.getUserId() == null ? null : authors.get(comment.getUserId());
        return IncidentCommentResponse.fromEntity(
                comment,
                author == null ? null : author.username(),
                author == null ? null : author.role());
    }

    private record Author(String username, MembershipRole role) {
    }
}
