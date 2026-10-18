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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.bank.users.user.exception.InvalidUserUpdateException;
import com.bank.users.user.exception.NonUpdatableFieldException;

import lombok.extern.slf4j.Slf4j;

/** Traduce excepciones al contrato público sin exponer detalles internos. */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldError> details = exception.getBindingResult().getAllErrors().stream()
                .map(error -> new FieldError(
                        error instanceof org.springframework.validation.FieldError fieldError
                                ? fieldError.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "La solicitud tiene datos inválidos", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraints(ConstraintViolationException exception) {
        List<FieldError> details = exception.getConstraintViolations().stream()
                .map(violation -> new FieldError(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "La solicitud tiene datos inválidos", details);
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof NonUpdatableFieldException fieldException) {
                return handleNonUpdatableField(fieldException);
            }
            if (cause instanceof InvalidUserUpdateException updateException) {
                return handleInvalidUpdate(updateException);
            }
            cause = cause.getCause();
        }
        return build(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "Solicitud inválida", List.of());
    }

    @ExceptionHandler(NonUpdatableFieldException.class)
    public ResponseEntity<ErrorResponse> handleNonUpdatableField(NonUpdatableFieldException exception) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, exception.getMessage(),
                List.of(new FieldError(exception.getField(), exception.getMessage())));
    }

    @ExceptionHandler(InvalidUserUpdateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUpdate(InvalidUserUpdateException exception) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "La solicitud tiene datos inválidos",
                List.of(new FieldError(exception.getField(), exception.getMessage())));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException exception) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException exception) {
        return build(HttpStatus.CONFLICT, ErrorCode.CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Error inesperado de tipo {}", exception.getClass().getName());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Error interno", List.of());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, ErrorCode code, String message,
            List<FieldError> details) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, details, Instant.now()));
    }
}
