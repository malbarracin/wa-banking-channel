package com.ingenia.wa.users.api.error;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

/**
 * Manejo global de excepciones. Expone el error contract sin stacktrace al cliente.
 *
 * @author Ingenia
 * @since 2026-10-19
 * Contacto: marcelo.albarracin@ingenia.la
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String CODE_VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final String CODE_BAD_REQUEST = "BAD_REQUEST";
    private static final String CODE_NOT_FOUND = "NOT_FOUND";
    private static final String CODE_INTERNAL_ERROR = "INTERNAL_ERROR";

    /**
     * Bean Validation failures → 400 VALIDATION_ERROR.
     *
     * @param exception validación fallida
     * @return respuesta de error
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<Object> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()))
                .collect(Collectors.toList());

        log.warn("Validation error: {} field(s)", details.size());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(CODE_VALIDATION_ERROR, "Request validation failed", details));
    }

    /**
     * Argumentos ilegales / transición inválida → 400 BAD_REQUEST.
     *
     * @param exception argumento ilegal
     * @return respuesta de error
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException exception) {
        log.warn("Bad request: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(CODE_BAD_REQUEST, exception.getMessage()));
    }

    /**
     * Documento duplicado → 409 Conflict con code BAD_REQUEST (contrato plan H1).
     *
     * @param exception unicidad violada
     * @return respuesta de error
     */
    @ExceptionHandler(DuplicateDocumentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateDocumentException exception) {
        log.warn("Duplicate document: {}/{}", exception.getDocumentType(), exception.getDocumentNumber());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(CODE_BAD_REQUEST, exception.getMessage(), exception.getDetails()));
    }

    /**
     * Recurso no encontrado → 404 NOT_FOUND.
     *
     * @param exception recurso ausente
     * @return respuesta de error
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException exception) {
        log.warn("Not found: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(CODE_NOT_FOUND, exception.getMessage()));
    }

    /**
     * Errores no controlados → 500 INTERNAL_ERROR (sin stacktrace).
     *
     * @param exception fallo inesperado
     * @return respuesta de error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInternal(Exception exception) {
        log.error("Unexpected error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(CODE_INTERNAL_ERROR, "An unexpected error occurred"));
    }
}
