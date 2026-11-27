package com.bcra.channel.ws.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.bcra.channel.ws.config.RequireInternalRole.InternalRole;
import com.bcra.channel.ws.exception.ForbiddenException;

import lombok.RequiredArgsConstructor;

/**
 * Autorización interna del piloto: valida {@code X-Internal-Role} contra
 * {@code bank.channel.internal-roles.*}. Reutilizable para historial, bloqueo y desvinculación.
 */
@Component
@RequiredArgsConstructor
public class BankInternalAuthFilter implements HandlerInterceptor {

    public static final String HEADER_NAME = "X-Internal-Role";

    private final BankChannelProperties bankChannelProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireInternalRole annotation = handlerMethod.getMethodAnnotation(RequireInternalRole.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequireInternalRole.class);
        }
        if (annotation == null) {
            return true;
        }

        String providedRole = request.getHeader(HEADER_NAME);
        if (providedRole == null || providedRole.isBlank()) {
            throw new IllegalArgumentException("Header X-Internal-Role requerido");
        }

        String expectedRole = resolveExpectedRole(annotation.value());
        if (!expectedRole.equals(providedRole)) {
            throw new ForbiddenException("Rol interno no autorizado");
        }
        return true;
    }

    /**
     * Valor configurado esperado para el rol (útil desde controladores o tests).
     *
     * @param role rol interno
     * @return valor de {@code bank.channel.internal-roles}
     */
    public String resolveExpectedRole(InternalRole role) {
        return switch (role) {
            case SUPPORT -> bankChannelProperties.getInternalRoles().getSupport();
            case BANK_POLICY -> bankChannelProperties.getInternalRoles().getBankPolicy();
        };
    }
}
