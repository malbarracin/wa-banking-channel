package com.wa.banking.channel.api.error;

import com.wa.banking.channel.exception.DuplicateLinkException;
import com.wa.banking.channel.exception.InvalidLinkStateException;
import com.wa.banking.channel.exception.LinkNotFoundException;
import com.wa.banking.channel.exception.UserCannotLinkException;
import com.wa.banking.channel.exception.VerificationBlockedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

/**
 * Manejador global de excepciones que implementa el contrato de error unificado.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse(ErrorCode.VALIDATION_ERROR, "Validation failed", details));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse(ErrorCode.BAD_REQUEST, exception.getMessage(), List.of()));
    }

    @ExceptionHandler(LinkNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleLinkNotFoundException(LinkNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildErrorResponse(ErrorCode.NOT_FOUND, exception.getMessage(), List.of()));
    }

    @ExceptionHandler(DuplicateLinkException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateLinkException(DuplicateLinkException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildErrorResponse(ErrorCode.BAD_REQUEST, exception.getMessage(), List.of()));
    }

    @ExceptionHandler({UserCannotLinkException.class, InvalidLinkStateException.class,
            VerificationBlockedException.class})
    public ResponseEntity<ErrorResponse> handleDomainBadRequest(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse(ErrorCode.BAD_REQUEST, exception.getMessage(), List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception exception) {
        log.error("Unexpected error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorResponse(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", List.of()));
    }

    private ErrorResponse buildErrorResponse(ErrorCode code, String message, List<String> details) {
        return ErrorResponse.builder()
                .code(code.name())
                .message(message)
                .details(details)
                .timestamp(Instant.now())
                .build();
    }
}
