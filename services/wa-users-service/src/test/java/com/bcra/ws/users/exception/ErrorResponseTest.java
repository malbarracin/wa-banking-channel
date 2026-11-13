package com.bcra.ws.users.exception;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ErrorResponse} factory helpers.
 */
class ErrorResponseTest {

    @Test
    @DisplayName("Should build error with empty details when omitted")
    void shouldBuildWithEmptyDetails_whenOfWithoutDetails() {
        ErrorResponse response = ErrorResponse.of(ErrorCode.BAD_REQUEST, "oops");

        assertThat(response.code()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(response.message()).isEqualTo("oops");
        assertThat(response.details()).isEmpty();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should copy details and default timestamp when null")
    void shouldDefaultTimestamp_whenNull() {
        ErrorResponse response = new ErrorResponse(
                ErrorCode.NOT_FOUND,
                "missing",
                List.of(new FieldError("id", "invalid")),
                null);

        assertThat(response.details()).hasSize(1);
        assertThat(response.timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("Should accept null details as empty list")
    void shouldUseEmptyList_whenDetailsNull() {
        ErrorResponse response = new ErrorResponse(
                ErrorCode.INTERNAL_ERROR, "x", null, Instant.parse("2026-10-19T12:00:00Z"));

        assertThat(response.details()).isEmpty();
        assertThat(response.timestamp()).isEqualTo(Instant.parse("2026-10-19T12:00:00Z"));
    }

    @Test
    @DisplayName("Should build with details via factory")
    void shouldBuildWithDetails_whenOfWithDetails() {
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.VALIDATION_ERROR,
                "failed",
                List.of(new FieldError("email", "invalid")));

        assertThat(response.details()).hasSize(1);
    }
    @Test
    @DisplayName("Error details cannot change after the response is created")
    void shouldProtectDetailsFromMutation() {
        FieldError detail = new FieldError("email", "Invalid value");
        List<FieldError> details = new ArrayList<>(List.of(detail));
        ErrorResponse response = ErrorResponse.of(ErrorCode.VALIDATION_ERROR, "Invalid request", details);
        details.clear();

        assertThat(response.details()).containsExactly(detail);
        assertThatThrownBy(() -> response.details().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
