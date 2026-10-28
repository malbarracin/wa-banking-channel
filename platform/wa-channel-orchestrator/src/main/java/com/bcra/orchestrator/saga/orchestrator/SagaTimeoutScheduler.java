package com.bcra.orchestrator.saga.orchestrator;

import java.time.Instant;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.bcra.orchestrator.saga.repository.SagaInstanceRepository;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class SagaTimeoutScheduler {
    private final SagaInstanceRepository sagas;
    private final SagaOrchestrator orchestrator;

    @Scheduled(fixedDelayString = "${SAGA_TIMEOUT_CHECK_INTERVAL_MS:60000}")
    public void expireSagas() {
        Instant now = Instant.now();
        sagas.findExpired(now).forEach(id -> orchestrator.expire(id, now));
    }
}
