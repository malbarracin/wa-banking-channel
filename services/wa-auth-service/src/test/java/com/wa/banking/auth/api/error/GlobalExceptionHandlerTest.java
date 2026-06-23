package com.wa.banking.auth.api.error;

import com.wa.banking.auth.exception.CredentialAlreadyRevokedException;
import com.wa.banking.auth.exception.CredentialNotFoundException;
import com.wa.banking.auth.exception.InvalidCredentialException;
import com.wa.banking.auth.exception.UserNotEligibleException;
import com.wa.banking.auth.exception.VerificationRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("Should map validation errors to 400 VALIDATION_ERROR")
    void shouldMapValidationError_whenBeanValidationFails() {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "request");
        bindingResult.addError(new FieldError("request", "channelLinkId", "channelLinkId is required"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getDetails()).contains("channelLinkId: channelLinkId is required");
    }

    @Test
    @DisplayName("Should map IllegalArgumentException to 400 BAD_REQUEST")
    void shouldMapBadRequest_whenIllegalArgument() {
        ResponseEntity<ErrorResponse> response =
                handler.handleIllegalArgumentException(new IllegalArgumentException("invalid input"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("BAD_REQUEST");
    }

    @Test
    @DisplayName("Should map CredentialNotFoundException to 404 NOT_FOUND")
    void shouldMapNotFound_whenCredentialMissing() {
        ResponseEntity<ErrorResponse> response =
                handler.handleCredentialNotFoundException(new CredentialNotFoundException("cred-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("Should map domain exceptions to 400 BAD_REQUEST")
    void shouldMapBadRequest_whenDomainException() {
        ResponseEntity<ErrorResponse> verification = handler.handleDomainBadRequest(new VerificationRequiredException());
        ResponseEntity<ErrorResponse> notEligible = handler.handleDomainBadRequest(new UserNotEligibleException("user-1"));
        ResponseEntity<ErrorResponse> invalid = handler.handleDomainBadRequest(
                new InvalidCredentialException("expired"));
        ResponseEntity<ErrorResponse> revoked = handler.handleDomainBadRequest(
                new CredentialAlreadyRevokedException("cred-1"));

        assertThat(verification.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(notEligible.getBody().getCode()).isEqualTo("BAD_REQUEST");
        assertThat(invalid.getBody().getCode()).isEqualTo("BAD_REQUEST");
        assertThat(revoked.getBody().getCode()).isEqualTo("BAD_REQUEST");
    }

    @Test
    @DisplayName("Should map unexpected errors to 500 INTERNAL_ERROR")
    void shouldMapInternalError_whenUnexpectedException() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGenericException(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
    }
}
