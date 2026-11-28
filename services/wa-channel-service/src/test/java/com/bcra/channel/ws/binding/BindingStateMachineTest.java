package com.bcra.channel.ws.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.bcra.channel.ws.binding.entity.BindingStatus;
import com.bcra.channel.ws.binding.exception.BindingConflictException;
import com.bcra.channel.ws.binding.service.BindingStateMachine;
import com.bcra.channel.ws.exception.ConflictException;

class BindingStateMachineTest {

    private final BindingStateMachine stateMachine = new BindingStateMachine();

    static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(BindingStatus.SIN_VINCULO, BindingStatus.PENDIENTE_VERIFICACION),
                Arguments.of(BindingStatus.PENDIENTE_VERIFICACION, BindingStatus.ACTIVO),
                Arguments.of(BindingStatus.PENDIENTE_VERIFICACION, BindingStatus.BLOQUEADO),
                Arguments.of(BindingStatus.ACTIVO, BindingStatus.BLOQUEADO),
                Arguments.of(BindingStatus.ACTIVO, BindingStatus.DESVINCULADO),
                Arguments.of(BindingStatus.BLOQUEADO, BindingStatus.ACTIVO),
                Arguments.of(BindingStatus.BLOQUEADO, BindingStatus.DESVINCULADO),
                Arguments.of(BindingStatus.BLOQUEADO, BindingStatus.PENDIENTE_VERIFICACION),
                Arguments.of(BindingStatus.DESVINCULADO, BindingStatus.PENDIENTE_VERIFICACION));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("allowedTransitions")
    @DisplayName("Transición permitida no lanza excepción")
    void allowedTransitionPasses(BindingStatus from, BindingStatus to) {
        stateMachine.assertTransition(from, to);
        assertThat(stateMachine.isAllowed(from, to)).isTrue();
    }

    @Test
    @DisplayName("Cada transición no permitida lanza ConflictException")
    void disallowedTransitionsThrowConflict() {
        Set<List<BindingStatus>> allowed = Set.of(
                List.of(BindingStatus.SIN_VINCULO, BindingStatus.PENDIENTE_VERIFICACION),
                List.of(BindingStatus.PENDIENTE_VERIFICACION, BindingStatus.ACTIVO),
                List.of(BindingStatus.PENDIENTE_VERIFICACION, BindingStatus.BLOQUEADO),
                List.of(BindingStatus.ACTIVO, BindingStatus.BLOQUEADO),
                List.of(BindingStatus.ACTIVO, BindingStatus.DESVINCULADO),
                List.of(BindingStatus.BLOQUEADO, BindingStatus.ACTIVO),
                List.of(BindingStatus.BLOQUEADO, BindingStatus.DESVINCULADO),
                List.of(BindingStatus.BLOQUEADO, BindingStatus.PENDIENTE_VERIFICACION),
                List.of(BindingStatus.DESVINCULADO, BindingStatus.PENDIENTE_VERIFICACION));

        for (BindingStatus from : BindingStatus.values()) {
            for (BindingStatus to : BindingStatus.values()) {
                if (allowed.contains(List.of(from, to))) {
                    continue;
                }
                assertThatThrownBy(() -> stateMachine.assertTransition(from, to))
                        .isInstanceOf(ConflictException.class)
                        .isInstanceOf(BindingConflictException.class)
                        .hasMessageContaining(from.name())
                        .hasMessageContaining(to.name());
                assertThat(stateMachine.isAllowed(from, to)).isFalse();
            }
        }
        assertThat(EnumSet.allOf(BindingStatus.class)).hasSize(5);
    }
}
