package com.monitoring.alerts.dto.response;

import com.monitoring.alerts.entity.IncidentComment;
import com.monitoring.organization.entity.MembershipRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Public representation of one incident thread message. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentCommentResponse {

    private Long id;
    private Long incidentId;
    private Long userId;
    private String username;
    /** Caller's role in the incident organization; {@code null} if the account left it. */
    private MembershipRole role;
    private String text;
    /** True for platform-generated lifecycle confirmations. */
    private boolean system;
    private Instant createdAt;
    private Instant updatedAt;

    public static IncidentCommentResponse fromEntity(
            IncidentComment comment, String username, MembershipRole role) {
        return IncidentCommentResponse.builder()
                .id(comment.getId())
                .incidentId(comment.getIncidentId())
                .userId(comment.getUserId())
                .username(username)
                .role(role)
                .text(comment.getText())
                .system(comment.isSystem())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
