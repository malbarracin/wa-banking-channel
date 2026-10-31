package com.bcra.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookInputFilterTest {
    @ParameterizedTest
    @ValueSource(strings = {"/webhook/twilio", "/webhooks/twilio/status",
        "/webhook;v=1/twilio;v=1", "/webhooks;v=1/twilio/status;v=1"})
    void rejectsDeclaredOversizeWithoutReading(String path) throws Exception {
        var request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getServletPath()).thenReturn(path);
        when(request.getRequestURI()).thenReturn(path);
        when(request.getContentLengthLong()).thenReturn(16385L);
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        new WebhookInputFilter().doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(413);
        verify(request, never()).getInputStream();
        org.mockito.Mockito.verifyNoInteractions(chain);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/webhook/twilio", "/webhooks/twilio/status"})
    void boundsUnknownLengthReadBeforeDispatch(String path) throws Exception {
        var reads = new AtomicInteger();
        var stream = new org.springframework.mock.web.DelegatingServletInputStream(
                new java.io.ByteArrayInputStream(new byte[1024 * 1024]) {
                    @Override
                    public synchronized int read(byte[] bytes, int offset, int length) {
                        int count = super.read(bytes, offset, length);
                        if (count > 0) {
                            reads.addAndGet(count);
                        }
                        return count;
                    }

                    @Override
                    public synchronized int read() {
                        int value = super.read();
                        if (value >= 0) {
                            reads.incrementAndGet();
                        }
                        return value;
                    }
                });
        var request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getServletPath()).thenReturn(path);
        when(request.getRequestURI()).thenReturn(path);
        when(request.getContentLengthLong()).thenReturn(-1L);
        when(request.getInputStream()).thenReturn(stream);
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        new WebhookInputFilter().doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(reads.get()).isEqualTo(16385);
        org.mockito.Mockito.verifyNoInteractions(chain);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/webhook/twilio", "/webhooks/twilio/status"})
    void rateLimitRejectsBeforeReadingAndBoundaryBodyIsPreserved(String path) throws Exception {
        var filter = new WebhookInputFilter();
        byte[] body = new byte[16384];
        for (int index = 0; index < 120; index++) {
            var request = new MockHttpServletRequest("POST", path);
            request.setContent(body);
            filter.doFilter(request, new MockHttpServletResponse(), (accepted, response) -> {
                var input = accepted.getInputStream();
                assertThat(input.isReady()).isTrue();
                assertThat(input.isFinished()).isFalse();
                assertThat(input.readAllBytes()).isEqualTo(body);
                assertThat(input.isFinished()).isTrue();
            });
        }
        var rejected = mock(HttpServletRequest.class);
        when(rejected.getMethod()).thenReturn("POST");
        when(rejected.getServletPath()).thenReturn(path);
        when(rejected.getRequestURI()).thenReturn(path);
        when(rejected.getRemoteAddr()).thenReturn("127.0.0.1");
        var response = new MockHttpServletResponse();
        filter.doFilter(rejected, response, mock(FilterChain.class));
        assertThat(response.getStatus()).isEqualTo(429);
        verify(rejected, never()).getInputStream();
    }
}
