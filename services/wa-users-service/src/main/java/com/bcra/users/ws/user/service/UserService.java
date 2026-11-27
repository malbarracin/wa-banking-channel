package com.bcra.users.ws.user.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.bcra.users.ws.audit.entity.AuditResult;
import com.bcra.users.ws.audit.service.AuditService;
import com.bcra.users.ws.config.BankUsersProperties;
import com.bcra.users.ws.exception.FieldError;
import com.bcra.users.ws.user.dto.CreateUserRequest;
import com.bcra.users.ws.user.dto.UpdateUserRequest;
import com.bcra.users.ws.user.dto.UserResponse;
import com.bcra.users.ws.user.entity.User;
import com.bcra.users.ws.user.entity.UserStatus;
import com.bcra.users.ws.user.exception.DuplicateUserException;
import com.bcra.users.ws.user.exception.UserNotFoundException;
import com.bcra.users.ws.user.exception.UserValidationException;
import com.bcra.users.ws.user.mapper.UserMapper;
import com.bcra.users.ws.user.repository.UserRepository;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alta y consulta de clientes según las políticas configuradas. */
@Service
public class UserService {
    private final UserRepository repository;
    private final UserMapper mapper;
    private final AuditService audit;
    private final BankUsersProperties policy;
    private final Validator validator;
    private final JdbcTemplate jdbc;

    public UserService(UserRepository repository, UserMapper mapper, AuditService audit, BankUsersProperties policy,
            Validator validator, JdbcTemplate jdbc) {
        this.repository = repository;
        this.mapper = mapper;
        this.audit = audit;
        this.policy = policy;
        this.validator = validator;
        this.jdbc = jdbc;
    }

    /** Crea y audita un cliente en una sola transacción. */
    @Transactional
    public UserResponse create(CreateUserRequest request, String actor) {
        List<FieldError> errors = new ArrayList<>();
        validator.validate(request).forEach(violation -> errors.add(
                new FieldError(violation.getPropertyPath().toString(), violation.getMessage())));
        Set<String> required = new java.util.HashSet<>(policy.requiredFields());
        required.add(policy.uniquenessField());
        for (String field : required) {
            String value = value(request, field);
            if (value == null || value.isBlank()) {
                errors.add(new FieldError(field, "Campo obligatorio"));
            }
        }
        if (!errors.isEmpty()) {
            throw new UserValidationException(errors);
        }
        String uniqueValue = value(request, policy.uniquenessField());
        jdbc.update("UPDATE user_creation_lock SET lock_id = lock_id WHERE lock_id = 1");
        boolean exists = switch (policy.uniquenessField()) {
            case "uniquenessValue" -> repository.existsByUniquenessField(uniqueValue);
            case "displayName" -> repository.existsByDisplayName(uniqueValue);
            case "contactEmail" -> repository.existsByContactEmail(uniqueValue);
            case "contactPhone" -> repository.existsByContactPhone(uniqueValue);
            default -> throw new IllegalStateException("Campo de política desconocido");
        };
        if (exists) {
            throw new DuplicateUserException(policy.uniquenessField());
        }
        Instant now = Instant.now();
        User user = mapper.toEntity(request);
        user.setId(UUID.randomUUID());
        user.setExternalId(UUID.randomUUID().toString());
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        repository.saveAndFlush(user);
        audit.registerCreation(user.getId(), actor == null || actor.isBlank() ? "system" : actor,
                AuditResult.OK, now);
        return filter(mapper.toResponse(user), true);
    }

    /** Consulta un cliente por su identificador interno. */
    @Transactional(readOnly = true)
    public UserResponse find(UUID id) {
        return filter(mapper.toResponse(repository.findById(id).orElseThrow(UserNotFoundException::new)), false);
    }

    /** Actualiza y audita los campos autorizados en una sola transacción. */
    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request, Set<String> fields, String actor) {
        User user = repository.findById(id).orElseThrow(UserNotFoundException::new);
        List<FieldError> errors = new ArrayList<>();
        validator.validate(request).forEach(violation -> errors.add(
                new FieldError(violation.getPropertyPath().toString(), violation.getMessage())));
        if (!errors.isEmpty()) {
            throw new UserValidationException(errors);
        }
        Set<String> changedFields = new java.util.HashSet<>(fields);
        changedFields.removeIf(field -> switch (field) {
            case "displayName" -> Objects.equals(user.getDisplayName(), request.displayName());
            case "contactEmail" -> Objects.equals(user.getContactEmail(), request.contactEmail());
            case "contactPhone" -> Objects.equals(user.getContactPhone(), request.contactPhone());
            default -> throw new IllegalArgumentException("Campo de actualización desconocido");
        });
        if (changedFields.isEmpty()) {
            return filter(mapper.toResponse(user), false);
        }
        mapper.updateEntity(request, user);
        Instant now = Instant.now();
        user.setUpdatedAt(now);
        repository.saveAndFlush(user);
        audit.registerModification(id, actor == null || actor.isBlank() ? "system" : actor,
                AuditResult.OK, now, changedFields);
        return filter(mapper.toResponse(user), false);
    }

    private String value(CreateUserRequest request, String field) {
        return switch (field) {
            case "uniquenessValue" -> request.uniquenessValue();
            case "displayName" -> request.displayName();
            case "contactEmail" -> request.contactEmail();
            case "contactPhone" -> request.contactPhone();
            default -> throw new IllegalStateException("Campo de política desconocido: " + field);
        };
    }

    private UserResponse filter(UserResponse response, boolean includeId) {
        List<String> fields = policy.publicFields();
        return new UserResponse(includeId || fields.contains("id") ? response.id() : null,
                fields.contains("externalId") ? response.externalId() : null,
                fields.contains("uniquenessValue") ? response.uniquenessValue() : null,
                fields.contains("displayName") ? response.displayName() : null,
                fields.contains("contactEmail") ? response.contactEmail() : null,
                fields.contains("contactPhone") ? response.contactPhone() : null, response.status(),
                fields.contains("createdAt") ? response.createdAt() : null);
    }
}
