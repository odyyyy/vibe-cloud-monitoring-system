package com.monitoring.alerts.exception;

public class AlertRuleAccessDeniedException extends RuntimeException {

    public AlertRuleAccessDeniedException() {
        super("You do not have access to this alert rule");
    }
}
