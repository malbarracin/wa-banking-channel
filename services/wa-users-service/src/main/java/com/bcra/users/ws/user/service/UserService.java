package com.bcra.users.ws.user.service;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bcra.users.ws.audit.entity.AuditAction;
import com.bcra.users.ws.audit.entity.AuditResult;
import com.bcra.users.ws.audit.service.AuditService;
import com.bcra.users.ws.config.CurrentActor;
import com.bcra.users.ws.exception.DuplicateUserException;
import com.bcra.users.ws.exception.UserNotFoundException;
import com.bcra.users.ws.user.dto.CreateUserRequest;
import com.bcra.users.ws.user.dto.UpdateUserRequest;
import com.bcra.users.ws.user.dto.UserResponse;
import com.bcra.users.ws.user.entity.DocumentType;
import com.bcra.users.ws.user.entity.User;
import com.bcra.users.ws.user.entity.UserStatus;
import com.bcra.users.ws.user.mapper.UserMapper;
import com.bcra.users.ws.user.repository.UserRepository;

/** Casos de uso de administración de usuarios. */
@Service
public class UserService {
    private final UserRepository repository;
    private final UserMapper mapper;
    private final AuditService audit;
    private final CurrentActor actor;
    private final Counter created;
    private final Counter duplicates;

    public UserService(UserRepository repository, UserMapper mapper, AuditService audit, CurrentActor actor,
            MeterRegistry registry) {
        this.repository = repository;
        this.mapper = mapper;
        this.audit = audit;
        this.actor = actor;
        this.created = registry.counter("users.created");
        this.duplicates = registry.counter("users.duplicate.rejected");
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (repository.existsByTipoDocumentoAndNumeroDocumento(request.tipoDocumento(), request.numeroDocumento())) {
            duplicates.increment();
            throw new DuplicateUserException();
        }
        User user = mapper.toEntity(request);
        user.setEstado(UserStatus.ACTIVO);
        try {
            user = repository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // The database constraint is the final guard against concurrent registrations.
            Throwable cause = exception.getMostSpecificCause();
            if (cause.getMessage() == null || !cause.getMessage().toLowerCase(java.util.Locale.ROOT)
                    .contains("uk_users_documento")) {
                throw exception;
            }
            duplicates.increment();
            throw new DuplicateUserException();
        }
        CurrentActor.Actor current = actor.get().orElseThrow();
        audit.record(user.getId(), AuditAction.ALTA, AuditResult.EXITOSO, List.of(),
                MDC.get("correlationId"), current.role() + ":" + current.id());
        created.increment();
        return mapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        return mapper.toResponse(repository.findById(id).orElseThrow(UserNotFoundException::new));
    }

    /** Actualiza los campos permitidos y registra los nombres de los cambios en la misma transacción. */
    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = repository.findById(id).orElseThrow(UserNotFoundException::new);
        List<String> changed = new ArrayList<>();
        if (request.nombreTratamiento() != null && !Objects.equals(request.nombreTratamiento(), user.getNombreTratamiento())) {
            user.setNombreTratamiento(request.nombreTratamiento());
            changed.add("nombreTratamiento");
        }
        if (request.email() != null && !Objects.equals(request.email(), user.getEmail())) {
            user.setEmail(request.email());
            changed.add("email");
        }
        if (request.telefono() != null && !Objects.equals(request.telefono(), user.getTelefono())) {
            user.setTelefono(request.telefono());
            changed.add("telefono");
        }
        if (request.preferencias() != null && !Objects.equals(request.preferencias(), user.getPreferencias())) {
            user.setPreferencias(request.preferencias());
            changed.add("preferencias");
        }
        repository.flush();
        CurrentActor.Actor current = actor.get().orElseThrow();
        audit.record(id, AuditAction.ACTUALIZACION, AuditResult.EXITOSO, changed,
                MDC.get("correlationId"), current.role() + ":" + current.id());
        return mapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse findByDocument(DocumentType type, String number) {
        return mapper.toResponse(repository.findByTipoDocumentoAndNumeroDocumento(type, number)
                .orElseThrow(UserNotFoundException::new));
    }
}
