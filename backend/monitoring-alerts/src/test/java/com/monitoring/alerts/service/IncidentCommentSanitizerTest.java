package com.monitoring.alerts.service;

import com.monitoring.alerts.exception.InvalidIncidentCommentException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentCommentSanitizerTest {

    @Test
    void removesMarkupAndNormalizesLineEndings() {
        String sanitized = IncidentCommentSanitizer.sanitize(
                "  <script>alert(1)</script>Investigating\r\n**DB CPU high**  ");

        assertEquals("Investigating\n**DB CPU high**", sanitized);
    }

    @Test
    void keepsPlainComparisonsAndMarkdown() {
        String text = "Check dashboard:456 and `p99 < 200ms`";
        String comparisons = "CPU was 95 > 90, latency 1 < 2 and <b>bold</b>";

        assertEquals(text, IncidentCommentSanitizer.sanitize(text));
        assertEquals("CPU was 95 > 90, latency 1 < 2 and bold",
                IncidentCommentSanitizer.sanitize(comparisons));
    }

    @Test
    void rejectsBlankOrTooLongText() {
        assertThrows(InvalidIncidentCommentException.class,
                () -> IncidentCommentSanitizer.sanitize("<b> </b>"));
        assertThrows(InvalidIncidentCommentException.class,
                () -> IncidentCommentSanitizer.sanitize("x".repeat(2001)));
    }
}
