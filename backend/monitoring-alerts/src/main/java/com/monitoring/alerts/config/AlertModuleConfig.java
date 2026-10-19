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
 * <p>Both executors are fixed-size with a bounded queue and a {@link ThreadPoolExecutor.CallerRunsPolicy}
 * rejection policy. Under sustained backpressure work runs on the submitting (scheduler) thread
 * instead of growing an unbounded queue or spawning threads, keeping resource usage bounded.
 */
@Configuration
public class AlertModuleConfig {

    @Bean(name = "alertEvaluationExecutor", destroyMethod = "shutdown")
    public ExecutorService alertEvaluationExecutor(
            @Value("${monitoring.alerts.evaluation-pool-size:4}") int poolSize,
            @Value("${monitoring.alerts.evaluation-queue-capacity:100}") int queueCapacity) {
        return boundedExecutor("alert-eval-", poolSize, queueCapacity);
    }

    @Bean(name = "alertWebhookExecutor", destroyMethod = "shutdown")
    public ExecutorService alertWebhookExecutor(
            @Value("${monitoring.alerts.webhook.pool-size:2}") int poolSize,
            @Value("${monitoring.alerts.webhook.queue-capacity:100}") int queueCapacity) {
        return boundedExecutor("alert-webhook-", poolSize, queueCapacity);
    }

    /**
     * Dedicated pool for email delivery so a slow SMTP server cannot starve webhook/Slack sends.
     */
    @Bean(name = "alertEmailExecutor", destroyMethod = "shutdown")
    public ExecutorService alertEmailExecutor(
            @Value("${monitoring.alerts.email.pool-size:1}") int poolSize,
            @Value("${monitoring.alerts.email.queue-capacity:100}") int queueCapacity) {
        return boundedExecutor("alert-email-", poolSize, queueCapacity);
    }

    private ExecutorService boundedExecutor(String threadPrefix, int poolSize, int queueCapacity) {
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
                new ThreadPoolExecutor.CallerRunsPolicy());
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
