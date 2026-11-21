package com.bcra.events.config;

import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ResourceLoader;

@Configuration(proxyBeanMethods = false)
public class StartupChecks {
    @Bean
    ApplicationRunner eventSchemasCheck(ResourceLoader resources) {
        return args -> {
            for (String name : List.of("envelope", "message-received", "message-ready-to-send", "message-status")) {
                if (!resources.getResource("classpath:contracts/events/" + name + ".v1.schema.json").exists()) {
                    throw new IllegalStateException("Missing event schema: " + name);
                }
            }
        };
    }

    @Bean
    @Profile("!local & !dev & !test")
    ApplicationRunner sharedKafkaCheck(KafkaHealthIndicator health) {
        return args -> {
            if (!Status.UP.equals(health.health().getStatus())) {
                throw new IllegalStateException("Kafka or required topics unavailable");
            }
        };
    }
}
