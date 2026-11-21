package com.bcra.users.ws.config;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper, JwtDecoder decoder) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, mapper, 401, "INVALID_TOKEN", "Identidad requerida"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, mapper, 403, "INSUFFICIENT_CLAIMS", "Acceso denegado")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*/audit")
                            .hasAnyRole("OPERACIONES", "SOPORTE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*")
                            .hasAnyRole("OPERACIONES", "SERVICIO_INTERNO")
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").hasRole("OPERACIONES")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/*", "/api/v1/users/*/status")
                            .hasRole("OPERACIONES")
                        .anyRequest().denyAll())
                .addFilterBefore(new ActorTokenFilter(decoder, mapper), AnonymousAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Profile("!test")
    JwtDecoder jwtDecoder(@Value("${app.gateway.jwt.jwk-set-uri}") String jwks,
            @Value("${app.gateway.jwt.issuer}") String issuer) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), jwt -> {
            boolean valid = jwt.getAudience() != null && jwt.getAudience().contains("wa-users-service") && jwt.getExpiresAt() != null
                    && jwt.getIssuedAt() != null && !jwt.getIssuedAt().isAfter(Instant.now())
                    && jwt.getId() != null && !jwt.getId().isBlank();
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        }));
        return decoder;
    }

    private static void writeError(HttpServletResponse response, ObjectMapper mapper, int status,
            String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), Map.of("code", code, "message", message,
                "details", List.of(), "timestamp", Instant.now().toString()));
    }

    static class ActorTokenFilter extends OncePerRequestFilter {
        private final JwtDecoder decoder;
        private final ObjectMapper mapper;

        ActorTokenFilter(JwtDecoder decoder, ObjectMapper mapper) {
            this.decoder = decoder;
            this.mapper = mapper;
        }
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String authorization = request.getHeader("Authorization");
            if (authorization != null) {
                if (!authorization.startsWith("Bearer ") || authorization.substring(7).isBlank()) {
                    writeError(response, mapper, 401, "INVALID_TOKEN", "Token inválido");
                    return;
                }
                Jwt jwt;
                try {
                    jwt = decoder.decode(authorization.substring(7));
                } catch (JwtValidationException exception) {
                    boolean expired = exception.getErrors().stream().anyMatch(error -> {
                        String description = error.getDescription();
                        return description != null && description.toLowerCase().contains("expired");
                    });
                    writeError(response, mapper, 401, expired ? "TOKEN_EXPIRED" : "INVALID_TOKEN", "Token inválido");
                    return;
                } catch (JwtException exception) {
                    writeError(response, mapper, 401, "INVALID_TOKEN", "Token inválido");
                    return;
                }
                String id = jwt.getSubject();
                Object roleClaim = jwt.getClaims().get("role");
                String role = roleClaim instanceof String value ? value : "";
                if (id == null || id.isBlank() || !List.of("OPERACIONES", "SOPORTE", "SERVICIO_INTERNO").contains(role)) {
                    writeError(response, mapper, 403, "INSUFFICIENT_CLAIMS", "Acceso denegado");
                    return;
                }
                var actor = new CurrentActor.Actor(id, role);
                var authentication = new UsernamePasswordAuthenticationToken(actor, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            chain.doFilter(request, response);
        }
    }
}
