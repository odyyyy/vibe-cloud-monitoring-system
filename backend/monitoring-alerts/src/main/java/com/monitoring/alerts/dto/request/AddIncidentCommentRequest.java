package com.monitoring.alerts.dto.request;

import com.monitoring.alerts.entity.IncidentComment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Adds a plain-text/markdown comment to an incident discussion thread. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddIncidentCommentRequest {

    @NotBlank(message = "Comment text is required")
    @Size(max = IncidentComment.MAX_TEXT_LENGTH,
            message = "Comment must be at most " + IncidentComment.MAX_TEXT_LENGTH + " characters")
    private String text;
}
