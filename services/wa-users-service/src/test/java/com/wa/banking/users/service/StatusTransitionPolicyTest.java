package com.wa.banking.users.service;

import com.wa.banking.users.entity.UserStatus;
import com.wa.banking.users.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitarios para la política de transiciones de estado operativo.
 */
class StatusTransitionPolicyTest {

    private StatusTransitionPolicy statusTransitionPolicy;

    @BeforeEach
    void setUp() {
        statusTransitionPolicy = new StatusTransitionPolicy();
    }

    @Test
    @DisplayName("Should allow transition from ACTIVE to SUSPENDED")
    void shouldAllowTransition_whenActiveToSuspended() {
        assertThatCode(() -> statusTransitionPolicy.validateTransition(UserStatus.ACTIVE, UserStatus.SUSPENDED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should allow transition from SUSPENDED to ACTIVE")
    void shouldAllowTransition_whenSuspendedToActive() {
        assertThatCode(() -> statusTransitionPolicy.validateTransition(UserStatus.SUSPENDED, UserStatus.ACTIVE))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw when target status equals current status")
    void shouldThrowException_whenSameStatus() {
        assertThatThrownBy(() -> statusTransitionPolicy.validateTransition(UserStatus.ACTIVE, UserStatus.ACTIVE))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("User is already in status ACTIVE");
    }

    @Test
    @DisplayName("Should throw when transitioning from SOFT_DELETED")
    void shouldThrowException_whenTransitionFromSoftDeleted() {
        assertThatThrownBy(() ->
                statusTransitionPolicy.validateTransition(UserStatus.SOFT_DELETED, UserStatus.ACTIVE))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("Transition from SOFT_DELETED to ACTIVE is not allowed");
    }

    @Test
    @DisplayName("Should throw when transitioning from ACTIVE to ACTIVE via duplicate request")
    void shouldThrowException_whenActiveToActive() {
        assertThatThrownBy(() -> statusTransitionPolicy.validateTransition(UserStatus.SUSPENDED, UserStatus.SUSPENDED))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("User is already in status SUSPENDED");
    }
}
