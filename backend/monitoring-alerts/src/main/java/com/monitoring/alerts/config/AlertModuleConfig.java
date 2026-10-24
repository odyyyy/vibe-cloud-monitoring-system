package com.monitoring.alerts.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Module infrastructure for alerts: the bounded executors used by evaluation and webhook
 * delivery.
 *
 * <p>All executors are fixed-size with a bounded queue. Evaluation uses a
 * {@link ThreadPoolExecutor.CallerRunsPolicy}, while notification executors use an
 * {@link ThreadPoolExecutor.AbortPolicy}: under backpressure a delivery is rejected and recorded
 * as failed instead of running on the alert evaluation thread. Queues and threads therefore stay
 * bounded without ever blocking rule evaluation on a slow customer endpoint.
 */
@Configuration
public class AlertModuleConfig {

    @Bean(name = "alertEvaluationExecutor", destroyMethod = "shutdown")
    public ExecutorService alertEvaluationExecutor(
            @Value("${monitoring.alerts.evaluation-pool-size:4}") int poolSize,
            @Value("${monitoring.alerts.evaluation-queue-capacity:100}") int queueCapacity) {
        return boundedExecutor(
                "alert-eval-", poolSize, queueCapacity, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Bean(name = "alertWebhookExecutor", destroyMethod = "shutdown")
    public ExecutorService alertWebhookExecutor(
            @Value("${monitoring.alerts.webhook.pool-size:2}") int poolSize,
            @Value("${monitoring.alerts.webhook.queue-capacity:100}") int queueCapacity) {
        return boundedExecutor(
                "alert-webhook-", poolSize, queueCapacity, new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Dedicated pool for email delivery so a slow SMTP server cannot starve webhook/Slack sends.
     */
    @Bean(name = "alertEmailExecutor", destroyMethod = "shutdown")
    public ExecutorService alertEmailExecutor(
            @Value("${monitoring.alerts.email.pool-size:1}") int poolSize,
            @Value("${monitoring.alerts.email.queue-capacity:100}") int queueCapacity) {
        return boundedExecutor(
                "alert-email-", poolSize, queueCapacity, new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Scheduled report generation is bounded independently from alert evaluation so a large
     * historical aggregation cannot delay alert processing.
     */
    @Bean(name = "alertReportExecutor", destroyMethod = "shutdown")
    public ExecutorService alertReportExecutor(
            @Value("${monitoring.alerts.reports.pool-size:2}") int poolSize,
            @Value("${monitoring.alerts.reports.queue-capacity:50}") int queueCapacity) {
        return boundedExecutor(
                "alert-report-", poolSize, queueCapacity, new ThreadPoolExecutor.AbortPolicy());
    }

    private ExecutorService boundedExecutor(
            String threadPrefix,
            int poolSize,
            int queueCapacity,
            java.util.concurrent.RejectedExecutionHandler rejectionHandler) {
        int effectivePoolSize = Math.max(1, poolSize);
        int effectiveQueueCapacity = Math.max(1, queueCapacity);
        ThreadFactory threadFactory = new NamedThreadFactory(threadPrefix);
        return new ThreadPoolExecutor(
                effectivePoolSize,
                effectivePoolSize,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(effectiveQueueCapacity),
                threadFactory,
                rejectionHandler);
    }

    private static final class NamedThreadFactory implements ThreadFactory {

        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger();

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
