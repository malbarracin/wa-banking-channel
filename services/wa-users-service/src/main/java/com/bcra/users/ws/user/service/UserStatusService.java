package com.bcra.users.ws.user.service;

import java.util.List;
import java.util.UUID;
import com.bcra.users.ws.audit.entity.AuditAction;
import com.bcra.users.ws.audit.entity.AuditResult;
import com.bcra.users.ws.audit.service.AuditService;
import com.bcra.users.ws.config.CurrentActor;
import com.bcra.users.ws.exception.InvalidStatusTransitionException;
import com.bcra.users.ws.exception.UserNotFoundException;
import com.bcra.users.ws.user.dto.ChangeStatusRequest;
import com.bcra.users.ws.user.dto.UserResponse;
import com.bcra.users.ws.user.entity.UserStatus;
import com.bcra.users.ws.user.mapper.UserMapper;
import com.bcra.users.ws.user.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserStatusService {
    private final UserRepository repository;
    private final UserMapper mapper;
    private final AuditService audit;
    private final CurrentActor actor;
    private final ApplicationEventPublisher events;
    private final Counter changes;

    public UserStatusService(UserRepository repository, UserMapper mapper, AuditService audit, CurrentActor actor,
            ApplicationEventPublisher events, MeterRegistry registry) {
        this.repository = repository;
        this.mapper = mapper;
        this.audit = audit;
        this.actor = actor;
        this.events = events;
        this.changes = registry.counter("users.status.changed");
    }

    @Transactional(noRollbackFor = InvalidStatusTransitionException.class)
    public UserResponse change(UUID id, ChangeStatusRequest request) {
        var user = repository.findById(id).orElseThrow(UserNotFoundException::new);
        var previous = user.getEstado();
        var next = request.nuevoEstado();
        var current = actor.get().orElseThrow();
        String correlationId = MDC.get("correlationId");
        String actorId = current.role() + ":" + current.id();
        if (previous == UserStatus.BAJA_LOGICA || previous == next) {
            audit.record(id, AuditAction.CAMBIO_ESTADO, AuditResult.RECHAZADO, List.of("estado"),
                    correlationId, actorId);
            throw new InvalidStatusTransitionException();
        }
        user.setEstado(next);
        repository.saveAndFlush(user);
        audit.record(id, AuditAction.CAMBIO_ESTADO, AuditResult.EXITOSO, List.of("estado"),
                correlationId, actorId);
        events.publishEvent(new StatusChangedEvent(id, previous, next, correlationId));
        changes.increment();
        return mapper.toResponse(user);
    }
}
