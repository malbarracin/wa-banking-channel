package com.bcra.gateway.messaging.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.slf4j.MDC;

@Configuration
public class InboundPublicationConfiguration {
    @Bean
    public ThreadPoolTaskExecutor inboundPublicationExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("inbound-kafka-");
        executor.setTaskDecorator(task -> {
            var context = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    if (context != null) {
                        MDC.setContextMap(context);
                    }
                    task.run();
                } finally {
                    MDC.clear();
                }
            };
        });
        return executor;
    }
}
