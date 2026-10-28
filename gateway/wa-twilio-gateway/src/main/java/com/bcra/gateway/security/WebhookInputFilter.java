package com.bcra.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(3)
public class WebhookInputFilter extends OncePerRequestFilter {
    private static final int MAX_BODY_BYTES = 16384;
    private static final int REQUESTS_PER_MINUTE = 120;
    private static final int MAX_TRACKED_ADDRESSES = 10000;
    private final Map<String, Integer> requestsByAddress = new HashMap<>();
    private long rateWindowStarted = System.nanoTime();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.isEmpty()) {
            path = request.getRequestURI().substring(request.getContextPath().length());
        }
        path = path.replaceAll(";[^/]*", "");
        return !"POST".equals(request.getMethod())
                || !("/webhook/twilio".equals(path) || "/webhooks/twilio/status".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getRequestURI().replaceAll(";[^/]*", "").endsWith("/status")
                ? "status" : "ip:" + request.getRemoteAddr();
        if (!allowRequest(key)) {
            response.setStatus(429);
            return;
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            response.setStatus(413);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            response.setStatus(413);
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
                return new BufferedBodyStream(body);
            }
        }, response);
    }

    private synchronized boolean allowRequest(String key) {
        long now = System.nanoTime();
        if (now - rateWindowStarted >= TimeUnit.MINUTES.toNanos(1)) {
            rateWindowStarted = now;
            requestsByAddress.clear();
        }
        int count = requestsByAddress.getOrDefault(key, 0);
        if (count >= REQUESTS_PER_MINUTE
                || (count == 0 && requestsByAddress.size() >= MAX_TRACKED_ADDRESSES)) {
            return false;
        }
        requestsByAddress.put(key, count + 1);
        return true;
    }

    private static final class BufferedBodyStream extends ServletInputStream {
        private final ByteArrayInputStream input;

        private BufferedBodyStream(byte[] body) {
            input = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return input.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            return input.read(bytes, offset, length);
        }

        @Override
        public boolean isFinished() {
            return input.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new IllegalStateException("Synchronous request body only");
        }
    }
}
