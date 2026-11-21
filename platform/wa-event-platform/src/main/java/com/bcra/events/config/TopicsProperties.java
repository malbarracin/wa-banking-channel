package com.bcra.events.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wa.topics")
public record TopicsProperties(String received, String readyToSend, String status, String dlq,
                               ConsumerGroups consumerGroups) {
    public List<String> names() {
        return List.of(received, readyToSend, status, dlq);
    }

    public record ConsumerGroups(String gateway, String orchestrator, String reference) {
    }
}
