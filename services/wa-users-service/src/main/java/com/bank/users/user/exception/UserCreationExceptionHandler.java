package com.bank.users.user.exception;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.bank.users.audit.entity.AuditAction;
import com.bank.users.audit.entity.AuditDetail;
import com.bank.users.audit.service.AuditService;
import com.bank.users.exception.ErrorResponse;
import com.bank.users.exception.GlobalExceptionHandler;
import com.bank.users.exception.NotFoundException;
import com.bank.users.user.controller.UserController;

import lombok.RequiredArgsConstructor;

/** Audita los rechazos de alta anteriores al service reutilizando el contrato global de errores. */
@RestControllerAdvice(assignableTypes = UserController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class UserCreationExceptionHandler {

    private final AuditService auditService;
    private final GlobalExceptionHandler globalHandler;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        auditService.recordRejection(null, AuditAction.ALTA, AuditDetail.DATOS_INVALIDOS);
        return globalHandler.handleValidation(exception);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
        auditService.recordRejection(null, AuditAction.ALTA, AuditDetail.DATOS_INVALIDOS);
        return globalHandler.handleBadRequest(exception);
    }

    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateUserException exception) {
        return globalHandler.handleConflict(exception);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException exception) {
        return globalHandler.handleNotFound(exception);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(MethodArgumentTypeMismatchException exception) {
        return globalHandler.handleBadRequest(exception);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        if ("POST".equals(request.getMethod())) {
            auditService.recordRejection(null, AuditAction.ALTA, AuditDetail.OPERACION_FALLIDA);
        }
        return globalHandler.handleUnexpected(exception);
    }
}
