package com.monitoring.alerts.service;

import com.monitoring.alerts.entity.IncidentComment;
import com.monitoring.alerts.exception.InvalidIncidentCommentException;

import java.util.regex.Pattern;

/**
 * Normalizes user-authored comment text before it is stored.
 *
 * <p>The frontend never renders raw HTML, but removing markup at the source still prevents
 * hostile payloads from being persisted and later exported or displayed by another client. The
 * sanitizer is deliberately conservative: it removes script/style blocks, HTML comments and
 * tags, rejects control characters, and keeps ordinary text, newlines and markdown syntax.
 */
final class IncidentCommentSanitizer {

    private static final Pattern SCRIPT_OR_STYLE =
            Pattern.compile("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>");
    private static final Pattern HTML_COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern HTML_TAG =
            Pattern.compile("(?s)</?[A-Za-z][^>]*>|<![A-Za-z][^>]*>");
    private static final Pattern CONTROL_CHARACTERS =
            Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]");

    private IncidentCommentSanitizer() {
    }

    /**
     * @return trimmed, markup-free text
     * @throws InvalidIncidentCommentException when nothing meaningful remains or the text is too
     *                                         long after normalization
     */
    static String sanitize(String raw) {
        if (raw == null) {
            throw new InvalidIncidentCommentException("Comment text is required");
        }

        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        text = SCRIPT_OR_STYLE.matcher(text).replaceAll("");
        text = HTML_COMMENT.matcher(text).replaceAll("");
        text = HTML_TAG.matcher(text).replaceAll("");
        text = CONTROL_CHARACTERS.matcher(text).replaceAll("");
        text = text.trim();

        if (text.isEmpty()) {
            throw new InvalidIncidentCommentException("Comment text is required");
        }
        if (text.length() > IncidentComment.MAX_TEXT_LENGTH) {
            throw new InvalidIncidentCommentException(
                    "Comment must be at most " + IncidentComment.MAX_TEXT_LENGTH + " characters");
        }
        return text;
    }
}
