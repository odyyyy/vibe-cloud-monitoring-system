package com.monitoring.alerts.service;

import java.util.Map;

/**
 * Rendered report payload. Exactly one of {@code html} (email) or {@code slackMessage}
 * (Slack Block Kit) is populated, matching the report channel type.
 */
public record ReportMessage(String subject, String html, Map<String, Object> slackMessage) {

    public static ReportMessage email(String subject, String html) {
        return new ReportMessage(subject, html, null);
    }

    public static ReportMessage slack(String subject, Map<String, Object> slackMessage) {
        return new ReportMessage(subject, null, slackMessage);
    }
}
