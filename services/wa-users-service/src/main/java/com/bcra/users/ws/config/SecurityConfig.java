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
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, mapper, 401, "UNAUTHORIZED", "Identidad requerida"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, mapper, 403, "FORBIDDEN", "Acceso denegado")))
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
                .addFilterBefore(new ActorHeaderFilter(), AnonymousAuthenticationFilter.class)
                .build();
    }

    private static void writeError(HttpServletResponse response, ObjectMapper mapper, int status,
            String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), Map.of("code", code, "message", message,
                "details", List.of(), "timestamp", Instant.now().toString()));
    }

    static class ActorHeaderFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String id = request.getHeader("X-Actor-Id");
            String role = request.getHeader("X-Actor-Role");
            if (id != null && !id.isBlank() && role != null && !role.isBlank()) {
                var actor = new CurrentActor.Actor(id, role);
                var authentication = new UsernamePasswordAuthenticationToken(actor, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            chain.doFilter(request, response);
        }
    }
}
