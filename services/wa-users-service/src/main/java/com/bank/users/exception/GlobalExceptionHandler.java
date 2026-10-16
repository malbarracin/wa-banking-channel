package com.bank.users.exception;

import java.time.Instant;
import java.util.List;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

/** Traduce excepciones al contrato HTTP sin publicar datos sensibles ni mensajes internos. */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldError> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), "El campo tiene un valor inválido"))
                .toList();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "La solicitud tiene datos inválidos", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        List<FieldError> details = exception.getConstraintViolations().stream()
                .map(violation -> new FieldError(violation.getPropertyPath().toString(),
                        "El campo tiene un valor inválido"))
                .toList();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "La solicitud tiene datos inválidos", details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException exception) {
        if (exception.isForReturnValue()) {
            return handleUnexpected(exception);
        }
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "La solicitud tiene datos inválidos", List.of());
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST,
                "La solicitud no es válida", List.of());
    }

    @ExceptionHandler({NotFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception exception) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND,
                "El recurso solicitado no existe", List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException exception) {
        return build(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                "La operación entra en conflicto con el estado actual", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Error inesperado al procesar la solicitud");
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "Error interno", List.of());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, ErrorCode code, String message,
            List<FieldError> details) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, details, Instant.now()));
    }
}
