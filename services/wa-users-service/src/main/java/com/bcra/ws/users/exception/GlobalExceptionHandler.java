package com.bcra.ws.users.exception;

import java.util.List;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.UnsatisfiedServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Traduce fallos HTTP y de dominio al contrato público sin registrar datos recibidos. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Validación del cuerpo de la solicitud. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldError> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(),
                        error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Request validation failed", details);
    }

    /** Validación de parámetros declarada con Bean Validation. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraints(ConstraintViolationException exception) {
        List<FieldError> details = exception.getConstraintViolations().stream()
                .map(violation -> new FieldError(violation.getPropertyPath().toString(), "Invalid value"))
                .toList();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Request validation failed", details);
    }

    /** JSON inválido y campos desconocidos, incluso dentro de objetos anidados. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof NonUpdatableFieldException invalidField) {
                return build(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST,
                        "Field is not updatable in this service",
                        List.of(new FieldError(invalidField.getField(), "Field is not updatable in this service")));
            }
            if (cause instanceof UnrecognizedPropertyException) {
                return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                        "Request validation failed", List.of(new FieldError("body", "Unknown field")));
            }
            cause = cause.getCause();
        }
        return build(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "Invalid request body", List.of());
    }

    /** Argumentos ausentes o inválidos; nunca devuelve mensajes de terceros. */
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, UnsatisfiedServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "Invalid request parameters", List.of());
    }

    /** Recursos inexistentes. */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException exception) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, exception.getMessage(), List.of());
    }

    /** Conflictos de unicidad y transición. */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException exception) {
        return build(HttpStatus.CONFLICT, ErrorCode.CONFLICT, exception.getMessage(), exception.getDetails());
    }

    /** Fallback sin mensajes ni stacktraces que puedan contener datos personales. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInternal(Exception exception) {
        log.error("Unexpected error while processing request");
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred", List.of());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, ErrorCode code, String message,
            List<FieldError> details) {
        return ResponseEntity.status(status).body(ErrorResponse.of(code, message, details));
    }
}
