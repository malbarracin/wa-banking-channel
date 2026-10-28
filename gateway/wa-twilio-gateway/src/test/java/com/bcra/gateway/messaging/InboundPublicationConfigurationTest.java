package com.bcra.gateway.messaging;

import com.bcra.gateway.messaging.config.InboundPublicationConfiguration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class InboundPublicationConfigurationTest {
    @Test
    void executorCopiesRequestMdcAndClearsWorkerBetweenRequests() throws Exception {
        var executor = new InboundPublicationConfiguration().inboundPublicationExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.initialize();
        try {
            MDC.put("correlationId", "request-context");
            var first = new CompletableFuture<String>();
            executor.execute(() -> first.complete(MDC.get("correlationId")));
            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("request-context");
            MDC.clear();
            var second = new CompletableFuture<String>();
            executor.execute(() -> second.complete(MDC.get("correlationId")));
            assertThat(second.get(2, TimeUnit.SECONDS)).isNull();
        } finally {
            MDC.clear();
            executor.shutdown();
        }
    }
}
