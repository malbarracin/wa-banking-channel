package com.bcra.ws.users.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ErrorResponse} factory helpers.
 */
class ErrorResponseTest {

    @Test
    @DisplayName("Should build error with empty details when omitted")
    void shouldBuildWithEmptyDetails_whenOfWithoutDetails() {
        ErrorResponse response = ErrorResponse.of("BAD_REQUEST", "oops");

        assertThat(response.code()).isEqualTo("BAD_REQUEST");
        assertThat(response.message()).isEqualTo("oops");
        assertThat(response.details()).isEmpty();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should copy details and default timestamp when null")
    void shouldDefaultTimestamp_whenNull() {
        ErrorResponse response = new ErrorResponse(
                "NOT_FOUND",
                "missing",
                List.of(Map.of("field", "id")),
                null);

        assertThat(response.details()).hasSize(1);
        assertThat(response.timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("Should accept null details as empty list")
    void shouldUseEmptyList_whenDetailsNull() {
        ErrorResponse response = new ErrorResponse("INTERNAL_ERROR", "x", null, Instant.parse("2026-10-19T12:00:00Z"));

        assertThat(response.details()).isEmpty();
        assertThat(response.timestamp()).isEqualTo(Instant.parse("2026-10-19T12:00:00Z"));
    }

    @Test
    @DisplayName("Should build with details via factory")
    void shouldBuildWithDetails_whenOfWithDetails() {
        ErrorResponse response = ErrorResponse.of(
                "VALIDATION_ERROR",
                "failed",
                List.of(Map.of("field", "email", "message", "invalid")));

        assertThat(response.details()).hasSize(1);
    }
}
