package com.bcra.gateway.web;

import com.bcra.gateway.messaging.producer.InboundWebhookPublisher;
import com.bcra.gateway.messaging.producer.StatusEventPublisher;
import com.bcra.gateway.normalization.StatusNormalizer;
import com.bcra.gateway.security.AntiReplayService;
import com.bcra.gateway.security.TwilioSignatureValidator;
import com.bcra.gateway.security.WebhookInputFilter;
import jakarta.servlet.ServletInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookBodyLimitMvcTest {
    @ParameterizedTest
    @CsvSource({
        "/webhook/twilio, -1, 16385",
        "/webhooks/twilio/status, -1, 16385",
        "/webhook/twilio, 1, 16385",
        "/webhooks/twilio/status, 1, 16385",
        "/webhook/twilio, 1048576, 0",
        "/webhooks/twilio/status, 1048576, 0"
    })
    void rejectsOversizedStreamBeforeMvcBinding(String path, long declaredLength, int expectedReads)
            throws Exception {
        var signatureValidator = mock(TwilioSignatureValidator.class);
        var replay = mock(AntiReplayService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<InboundWebhookPublisher> inboundPublisher = mock(ObjectProvider.class);
        var statusNormalizer = mock(StatusNormalizer.class);
        var statusPublisher = mock(StatusEventPublisher.class);
        var mvc = MockMvcBuilders.standaloneSetup(
                new TwilioWebhookController(signatureValidator, replay, inboundPublisher),
                new TwilioStatusController("synthetic-test-token", "https://example.test/status",
                        statusNormalizer, statusPublisher, replay))
                .addFilters(new WebhookInputFilter()).build();
        var reads = new AtomicInteger();
        // Generate a 1 MiB body lazily: the test fails if MVC consumes the full stream.
        ServletInputStream stream = new DelegatingServletInputStream(new InputStream() {
            @Override
            public int read() {
                if (reads.get() >= 1048576) {
                    return -1;
                }
                reads.incrementAndGet();
                return 'a';
            }
        });
        mvc.perform(servletContext -> {
            var request = new MockHttpServletRequest(servletContext) {
                @Override
                public long getContentLengthLong() {
                    return declaredLength;
                }

                @Override
                public int getContentLength() {
                    return (int) declaredLength;
                }

                @Override
                public ServletInputStream getInputStream() {
                    return stream;
                }
            };
            request.setMethod("POST");
            request.setRequestURI(path);
            request.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
            if (declaredLength == -1) {
                request.addHeader("Transfer-Encoding", "chunked");
            }
            return request;
        }).andExpect(status().isPayloadTooLarge());

        assertThat(reads.get()).isEqualTo(expectedReads);
        verifyNoInteractions(signatureValidator, replay, inboundPublisher, statusNormalizer, statusPublisher);
    }
}
