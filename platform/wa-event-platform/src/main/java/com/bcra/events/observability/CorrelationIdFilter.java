package com.bcra.events.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String candidate = request.getHeader(HEADER);
        String correlationId = candidate != null
                && candidate.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
                ? candidate : UUID.randomUUID().toString();
        String previous = MDC.get(HEADER);
        try {
            MDC.put(HEADER, correlationId);
            response.setHeader(HEADER, correlationId);
            log.debug("HTTP request correlation established");
            chain.doFilter(request, response);
        } finally {
            if (previous == null) {
                MDC.remove(HEADER);
            } else {
                MDC.put(HEADER, previous);
            }
        }
    }
}
