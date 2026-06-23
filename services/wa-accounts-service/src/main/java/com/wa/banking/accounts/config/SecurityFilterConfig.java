package com.wa.banking.accounts.config;

import com.wa.banking.accounts.api.error.ErrorCode;
import com.wa.banking.accounts.api.error.ErrorResponse;
import com.wa.banking.accounts.integration.session.SessionCredentialValidator;
import com.wa.banking.accounts.integration.session.ValidateCredentialResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Registra el filtro de autenticación por credencial H2 en rutas de API v1.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Configuration
public class SecurityFilterConfig {

    public static final String BANK_USER_ID_ATTRIBUTE = "bankUserId";

    public static final String CHANNEL_LINK_ID_ATTRIBUTE = "channelLinkId";

    public static final String CREDENTIAL_ID_HEADER = "X-Credential-Id";

    static final String UNAUTHORIZED_MESSAGE =
            "Por seguridad, verificá tu identidad para ver tus cuentas.";

    @Bean
    public FilterRegistrationBean<SessionAuthenticationFilter> sessionAuthenticationFilter(
            SessionCredentialValidator sessionCredentialValidator) {
        FilterRegistrationBean<SessionAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SessionAuthenticationFilter(sessionCredentialValidator));
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    /**
     * Filtro que valida credencial H2 antes de procesar operaciones de cuentas.
     */
    @Slf4j
    @RequiredArgsConstructor
    static class SessionAuthenticationFilter extends OncePerRequestFilter {

        private final SessionCredentialValidator sessionCredentialValidator;

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            String credentialId = request.getHeader(CREDENTIAL_ID_HEADER);

            if (authorization == null || !authorization.startsWith("Bearer ")) {
                log.warn("Rejected request: missing or invalid Authorization header");
                writeUnauthorized(response);
                return;
            }

            if (credentialId == null || credentialId.isBlank()) {
                log.warn("Rejected request: missing X-Credential-Id header");
                writeUnauthorized(response);
                return;
            }

            String token = authorization.substring("Bearer ".length()).trim();
            ValidateCredentialResponse validation = sessionCredentialValidator.validate(credentialId, token);

            if (validation == null || !validation.isValid()) {
                log.warn("Rejected request: invalid session credential {}", credentialId);
                writeUnauthorized(response);
                return;
            }

            request.setAttribute(BANK_USER_ID_ATTRIBUTE, validation.getBankUserId());
            request.setAttribute(CHANNEL_LINK_ID_ATTRIBUTE, validation.getChannelLinkId());
            filterChain.doFilter(request, response);
        }

        private void writeUnauthorized(HttpServletResponse response) throws IOException {
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code(ErrorCode.UNAUTHORIZED.name())
                    .message(UNAUTHORIZED_MESSAGE)
                    .details(java.util.List.of())
                    .timestamp(Instant.now())
                    .build();

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(toJson(errorResponse));
        }

        private String toJson(ErrorResponse errorResponse) {
            return """
                    {"code":"%s","message":"%s","details":[],"timestamp":"%s"}
                    """.formatted(
                    errorResponse.getCode(),
                    errorResponse.getMessage(),
                    errorResponse.getTimestamp());
        }
    }
}
