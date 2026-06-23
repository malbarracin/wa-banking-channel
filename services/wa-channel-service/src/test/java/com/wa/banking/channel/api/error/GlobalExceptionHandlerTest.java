package com.wa.banking.channel.api.error;

import com.wa.banking.channel.exception.DuplicateLinkException;
import com.wa.banking.channel.exception.InvalidLinkStateException;
import com.wa.banking.channel.exception.LinkNotFoundException;
import com.wa.banking.channel.exception.UserCannotLinkException;
import com.wa.banking.channel.exception.VerificationBlockedException;
import com.wa.banking.channel.entity.LinkStatus;
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
        bindingResult.addError(new FieldError("request", "phoneNumber", "phoneNumber is required"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getDetails()).contains("phoneNumber: phoneNumber is required");
    }

    @Test
    @DisplayName("Should map LinkNotFoundException to 404 NOT_FOUND")
    void shouldMapNotFound_whenLinkMissing() {
        ResponseEntity<ErrorResponse> response =
                handler.handleLinkNotFoundException(new LinkNotFoundException("link-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("Should map DuplicateLinkException to 409 BAD_REQUEST code")
    void shouldMapConflict_whenDuplicateLink() {
        ResponseEntity<ErrorResponse> response =
                handler.handleDuplicateLinkException(new DuplicateLinkException("+541112345678"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo("BAD_REQUEST");
    }

    @Test
    @DisplayName("Should map domain exceptions to 400 BAD_REQUEST")
    void shouldMapBadRequest_whenDomainException() {
        ResponseEntity<ErrorResponse> invalidState = handler.handleDomainBadRequest(
                new InvalidLinkStateException(LinkStatus.ACTIVE, "block"));
        ResponseEntity<ErrorResponse> userCannotLink = handler.handleDomainBadRequest(
                new UserCannotLinkException("inactive"));
        ResponseEntity<ErrorResponse> blocked = handler.handleDomainBadRequest(
                new VerificationBlockedException());

        assertThat(invalidState.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(userCannotLink.getBody().getCode()).isEqualTo("BAD_REQUEST");
        assertThat(blocked.getBody().getCode()).isEqualTo("BAD_REQUEST");
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
