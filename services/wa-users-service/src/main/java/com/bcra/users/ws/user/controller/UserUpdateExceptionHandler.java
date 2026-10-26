package com.bcra.users.ws.user.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.HandlerMapping;

import com.bcra.users.ws.exception.ErrorCode;
import com.bcra.users.ws.exception.ErrorResponse;
import com.bcra.users.ws.user.entity.AuditAction;
import com.bcra.users.ws.user.entity.AuditResult;
import com.bcra.users.ws.user.exception.NonEditableFieldException;
import com.bcra.users.ws.user.service.AuditService;

import lombok.RequiredArgsConstructor;

/** Traduce y audita rechazos del body antes de invocar la actualización. */
@RestControllerAdvice(assignableTypes = UserController.class)
@Order(0)
@RequiredArgsConstructor
public class UserUpdateExceptionHandler {

    private final AuditService auditService;

    /** Reutiliza el contrato único y nunca expone valores del JSON rechazado. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        Throwable causa = exception;
        while (causa.getCause() != null && !(causa instanceof NonEditableFieldException)) {
            causa = causa.getCause();
        }
        String mensaje = causa instanceof NonEditableFieldException
                ? "El campo no es editable por este servicio" : "La solicitud no es válida";
        Object atributos = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (atributos instanceof Map<?, ?> variables && variables.get("tipo") instanceof String tipo
                && variables.get("numero") instanceof String numero) {
            auditService.registrar(AuditAction.ACTUALIZACION, AuditResult.RECHAZADO, tipo, numero, mensaje);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ErrorCode.BAD_REQUEST, mensaje, List.of(), Instant.now()));
    }
}
