package com.bcra.credentials.ws.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.bcra.credentials.ws.credencial.controller.CredencialController;

import lombok.RequiredArgsConstructor;

/** Autoriza antes de deserializar el body o ejecutar operaciones de credenciales. */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class CallerSecurityConfig implements WebMvcConfigurer {

    private final CallerAuthorization authorization;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (handler instanceof HandlerMethod method
                        && CredencialController.class.isAssignableFrom(method.getBeanType())) {
                    switch (method.getMethod().getName()) {
                        case "emitir", "revocar" -> authorization.requireActor(
                                request.getHeader("Authorization"), false);
                        case "revocarMasivamente" -> authorization.requireActor(
                                request.getHeader("Authorization"), true);
                        default -> { }
                    }
                }
                return true;
            }
        });
    }
}
