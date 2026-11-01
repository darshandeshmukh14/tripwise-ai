package com.tripwise.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    public static final String TRIP_GENERATION_EXECUTOR = "tripGenerationExecutor";

    /**
     * Bounded pool for AI itinerary generation; a full queue rejects new work instead of growing unbounded.
     */
    @Bean(name = TRIP_GENERATION_EXECUTOR)
    public ThreadPoolTaskExecutor tripGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("trip-gen-");
        // Let in-flight AI calls (read timeout 60s) finish on graceful shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(70);
        return executor;
    }
}
