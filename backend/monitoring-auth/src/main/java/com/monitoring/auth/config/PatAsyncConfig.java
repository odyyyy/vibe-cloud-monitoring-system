package com.monitoring.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dedicated, bounded executor for best-effort Personal Access Token housekeeping.
 *
 * <p>The default Spring {@code @Async} executor has an unbounded queue, so a slow database
 * could turn token activity into unbounded memory growth. This executor keeps at most a
 * small number of threads and a fixed queue; under sustained pressure the oldest pending
 * {@code lastUsedAt} updates are discarded, which is acceptable because the timestamp is
 * diagnostic only.
 */
@Configuration
public class PatAsyncConfig {

    @Bean(name = "patExecutor")
    public ThreadPoolTaskExecutor patExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("pat-last-used-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        return executor;
    }
}
