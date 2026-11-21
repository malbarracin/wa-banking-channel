package com.bcra.users.ws;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import com.bcra.users.ws.audit.service.AuditService;
import com.bcra.users.ws.config.CurrentActor;
import com.bcra.users.ws.exception.InvalidStatusTransitionException;
import com.bcra.users.ws.user.dto.ChangeStatusRequest;
import com.bcra.users.ws.user.entity.User;
import com.bcra.users.ws.user.entity.UserStatus;
import com.bcra.users.ws.user.mapper.UserMapper;
import com.bcra.users.ws.user.repository.UserRepository;
import com.bcra.users.ws.user.service.UserStatusService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ApplicationEventPublisher;

class UserStatusServiceTest {
    static Stream<Arguments> transitions() {
        return Stream.of(
                Arguments.of(UserStatus.ACTIVO, UserStatus.SUSPENDIDO, true),
                Arguments.of(UserStatus.ACTIVO, UserStatus.BAJA_LOGICA, true),
                Arguments.of(UserStatus.SUSPENDIDO, UserStatus.ACTIVO, true),
                Arguments.of(UserStatus.SUSPENDIDO, UserStatus.BAJA_LOGICA, true),
                Arguments.of(UserStatus.ACTIVO, UserStatus.ACTIVO, false),
                Arguments.of(UserStatus.SUSPENDIDO, UserStatus.SUSPENDIDO, false),
                Arguments.of(UserStatus.BAJA_LOGICA, UserStatus.ACTIVO, false),
                Arguments.of(UserStatus.BAJA_LOGICA, UserStatus.SUSPENDIDO, false),
                Arguments.of(UserStatus.BAJA_LOGICA, UserStatus.BAJA_LOGICA, false));
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void validatesTransitions(UserStatus previous, UserStatus next, boolean allowed) {
        var repository = mock(UserRepository.class);
        var actor = mock(CurrentActor.class);
        var events = mock(ApplicationEventPublisher.class);
        var service = new UserStatusService(repository, mock(UserMapper.class), mock(AuditService.class), actor,
                events, new SimpleMeterRegistry());
        var user = new User();
        user.setEstado(previous);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(user));
        when(actor.get()).thenReturn(Optional.of(new CurrentActor.Actor("actor", "OPERACIONES")));
        var request = new ChangeStatusRequest(next, "motivo");
        if (allowed) {
            service.change(id, request);
            verify(events).publishEvent(any(Object.class));
        } else {
            assertThatThrownBy(() -> service.change(id, request)).isInstanceOf(InvalidStatusTransitionException.class);
            verify(events, never()).publishEvent(any(Object.class));
        }
    }
}
