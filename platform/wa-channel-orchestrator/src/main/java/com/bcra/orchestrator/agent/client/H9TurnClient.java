package com.bcra.orchestrator.agent.client;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.bcra.orchestrator.agent.dto.TurnRequest;
import com.bcra.orchestrator.agent.dto.TurnResponse;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Slf4j
public class H9TurnClient {

    private static final String CORRELATION_ID = "correlationId";
    private final WebClient client;
    private final String token;
    private final Duration timeout;
    private final int maxRetries;
    private final Duration backoff;

    public H9TurnClient(WebClient client, String token, Duration timeout, int maxRetries, Duration backoff) {
        this.client = Objects.requireNonNull(client, "client");
        this.token = Objects.requireNonNull(token, "token");
        if (timeout.isNegative() || timeout.isZero() || maxRetries < 0 || maxRetries > 10
                || backoff.isNegative() || backoff.isZero()) {
            throw new IllegalArgumentException("Invalid H9 retry configuration");
        }
        this.timeout = timeout;
        this.maxRetries = maxRetries;
        this.backoff = backoff;
    }

    /** Synchronous boundary: caller keeps conversation order and owns the state/outbox transaction. */
    public TurnResponse turn(TurnRequest request) {
        Objects.requireNonNull(request, "request");
        if (token.isBlank()) {
            throw new H9TurnException(H9TurnException.Reason.NOT_CONFIGURED);
        }
        String previous = MDC.get(CORRELATION_ID);
        MDC.put(CORRELATION_ID, request.correlationId().toString());
        long started = System.nanoTime();
        try {
            log.info("H9 turn request");
            TurnResponse response = client.post().uri("/api/v1/agent/turn")
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        headers.setBearerAuth(token);
                        headers.set("X-Correlation-Id", request.correlationId().toString());
                        headers.set("X-Conversation-Id", request.conversationId());
                        headers.set("X-Message-Id", request.messageId());
                        headers.set("X-Idempotency-Key", request.messageId());
                    }).bodyValue(request)
                    .exchangeToMono(http -> {
                        if (http.statusCode().value() == 200) {
                            return http.bodyToMono(TurnResponse.class)
                                    .switchIfEmpty(Mono.error(new H9TurnException(
                                            H9TurnException.Reason.INVALID_RESPONSE)));
                        }
                        var reason = http.statusCode().is5xxServerError()
                                ? H9TurnException.Reason.UNAVAILABLE : H9TurnException.Reason.REJECTED;
                        return http.releaseBody().then(Mono.<TurnResponse>error(new H9TurnException(reason)));
                    })
                    .timeout(timeout)
                    .onErrorMap(TimeoutException.class, error -> new H9TurnException(H9TurnException.Reason.TIMEOUT))
                    .onErrorMap(WebClientRequestException.class,
                            error -> new H9TurnException(H9TurnException.Reason.UNAVAILABLE))
                    // Transport/decoder exceptions may embed PII; expose only a safe domain reason.
                    .onErrorMap(error -> !(error instanceof H9TurnException),
                            error -> new H9TurnException(H9TurnException.Reason.INVALID_RESPONSE))
                    .retryWhen(Retry.backoff(maxRetries, backoff).maxBackoff(backoff.multipliedBy(16)).jitter(0)
                            .filter(error -> error instanceof H9TurnException failure && failure.isRetryable())
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                    .block();
            if (response == null || !request.correlationId().equals(response.correlationId())
                    || !request.conversationId().equals(response.conversationId())
                    || !request.messageId().equals(response.messageId())) {
                throw new H9TurnException(H9TurnException.Reason.INVALID_RESPONSE);
            }
            log.info("H9 turn response outcome=SUCCESS elapsedMs={}", elapsedMillis(started));
            return response;
        } catch (H9TurnException failure) {
            log.warn("H9 turn response outcome={} elapsedMs={}", failure.getReason(), elapsedMillis(started));
            throw failure;
        } finally {
            if (previous == null) {
                MDC.remove(CORRELATION_ID);
            } else {
                MDC.put(CORRELATION_ID, previous);
            }
        }
    }

    private long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }
}
