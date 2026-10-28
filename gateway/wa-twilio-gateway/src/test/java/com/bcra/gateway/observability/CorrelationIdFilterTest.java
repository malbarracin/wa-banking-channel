package com.bcra.gateway.observability;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;

class CorrelationIdFilterTest {
    @Test
    void generatesOrPropagatesUuidAndCleansMdcEvenOnFailure() throws Exception {
        for (String input : new String[] {"", "invalid\r\nvalue", UUID.randomUUID().toString()}) {
            var request = new MockHttpServletRequest();
            request.addHeader("correlationId", input);
            var response = new MockHttpServletResponse();
            new CorrelationIdFilter().doFilter(request, response, (req, res) -> {
                assertThat(MDC.get("correlationId")).isEqualTo(response.getHeader("correlationId"));
                assertThat(UUID.fromString(MDC.get("correlationId"))).isNotNull();
                if (input.length() == 36) {
                    assertThat(MDC.get("correlationId")).isEqualTo(input);
                }
            });
            assertThat(MDC.get("correlationId")).isNull();
        }
        MDC.put("correlationId", "previous");
        try {
            assertThatThrownBy(() -> new CorrelationIdFilter().doFilter(new MockHttpServletRequest(),
                    new MockHttpServletResponse(), (req, res) -> { throw new java.io.IOException(); }))
                    .isInstanceOf(java.io.IOException.class);
            assertThat(MDC.get("correlationId")).isEqualTo("previous");
        } finally {
            MDC.clear();
        }
    }
}
