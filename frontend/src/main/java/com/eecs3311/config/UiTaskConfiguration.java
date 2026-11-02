package com.eecs3311.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class UiTaskConfiguration {
    @Bean
    public ThreadPoolTaskExecutor summaryExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("summary-ui-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
