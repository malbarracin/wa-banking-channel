package com.bcra.orchestrator.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OutboxScheduler {
    private final OutboxDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:250}")
    public void publishPending() {
        dispatcher.dispatchNext();
    }
}
