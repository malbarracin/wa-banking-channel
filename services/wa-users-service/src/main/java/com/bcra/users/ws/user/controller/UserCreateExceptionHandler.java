package com.bcra.users.ws.user.controller;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.bcra.users.ws.exception.ErrorResponse;
import com.bcra.users.ws.exception.GlobalExceptionHandler;
import com.bcra.users.ws.user.dto.CreateUserRequest;
import com.bcra.users.ws.user.entity.AuditAction;
import com.bcra.users.ws.user.entity.AuditResult;
import com.bcra.users.ws.user.service.AuditService;

import lombok.RequiredArgsConstructor;

/** Audita altas rechazadas por validación cuando el documento permite identificar al solicitante. */
@RestControllerAdvice(assignableTypes = UserController.class)
@Order(0)
@RequiredArgsConstructor
public class UserCreateExceptionHandler {

    private static final int MAX_DOCUMENT_LENGTH = 255;

    private final AuditService auditService;
    private final GlobalExceptionHandler globalExceptionHandler;

    /** Conserva el contrato de validación y registra únicamente referencias de documento utilizables. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        if (exception.getBindingResult().getTarget() instanceof CreateUserRequest solicitud
                && solicitud.documento() != null
                && esReferenciaValida(solicitud.documento().tipo())
                && esReferenciaValida(solicitud.documento().numero())) {
            auditService.registrar(AuditAction.ALTA, AuditResult.RECHAZADO,
                    solicitud.documento().tipo(), solicitud.documento().numero(),
                    "Datos mínimos de alta inválidos");
        }
        return globalExceptionHandler.handleValidation(exception);
    }

    private boolean esReferenciaValida(String valor) {
        return valor != null && !valor.isBlank() && valor.length() <= MAX_DOCUMENT_LENGTH;
    }
}
