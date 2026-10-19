package com.monitoring.alerts.service;

import com.monitoring.alerts.event.AlertEvent;
import com.monitoring.alerts.entity.AlertRule;
import com.monitoring.alerts.repository.AlertRuleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Periodically evaluates active alert rules.
 *
 * <p>Evaluation runs on a dedicated bounded executor ({@code alertEvaluationExecutor}) rather than
 * the scheduler thread, so a large account cannot create unbounded concurrent work and the
 * schedule is not blocked by a single slow evaluation. Each task opens a short transaction; the
 * scheduler then dispatches notifications for the transitions that occurred, after those
 * transactions have committed.
 */
@Component
@Slf4j
public class AlertScheduler {

    private final AlertRuleRepository ruleRepository;
    private final AlertEvaluationService evaluationService;
    private final AlertNotificationDispatcher notificationDispatcher;
    private final ExecutorService alertEvaluationExecutor;
    private final long evaluationTimeoutMs;

    public AlertScheduler(
            AlertRuleRepository ruleRepository,
            AlertEvaluationService evaluationService,
            AlertNotificationDispatcher notificationDispatcher,
            @Qualifier("alertEvaluationExecutor") ExecutorService alertEvaluationExecutor,
            @Value("${monitoring.alerts.evaluation-timeout-ms:120000}") long evaluationTimeoutMs) {
        this.ruleRepository = ruleRepository;
        this.evaluationService = evaluationService;
        this.notificationDispatcher = notificationDispatcher;
        this.alertEvaluationExecutor = alertEvaluationExecutor;
        this.evaluationTimeoutMs = evaluationTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${monitoring.alerts.scheduler-interval-ms:60000}")
    public void evaluateActiveRules() {
        Instant now = Instant.now();
        List<Long> ruleIds = ruleRepository.findByActiveTrue().stream()
                .map(AlertRule::getId)
                .toList();
        if (ruleIds.isEmpty()) {
            return;
        }

        List<Callable<Optional<AlertEvent>>> tasks = ruleIds.stream()
                .map(id -> (Callable<Optional<AlertEvent>>) () -> evaluationService.evaluate(id, now))
                .toList();

        List<Future<Optional<AlertEvent>>> futures;
        try {
            futures = alertEvaluationExecutor.invokeAll(tasks, evaluationTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        }

        for (Future<Optional<AlertEvent>> future : futures) {
            try {
                future.get().ifPresent(notificationDispatcher::dispatch);
            } catch (CancellationException ex) {
                log.warn("Alert evaluation was cancelled after the {} ms timeout", evaluationTimeoutMs);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                log.warn("Alert evaluation failed: {}", cause.getMessage());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
