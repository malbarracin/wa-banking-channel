package com.bcra.users.ws.user.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.bcra.users.ws.audit.entity.AuditResult;
import com.bcra.users.ws.audit.service.AuditService;
import com.bcra.users.ws.config.BankUsersProperties;
import com.bcra.users.ws.exception.FieldError;
import com.bcra.users.ws.user.dto.CreateUserRequest;
import com.bcra.users.ws.user.dto.UserResponse;
import com.bcra.users.ws.user.entity.User;
import com.bcra.users.ws.user.entity.UserStatus;
import com.bcra.users.ws.user.exception.DuplicateUserException;
import com.bcra.users.ws.user.exception.UserNotFoundException;
import com.bcra.users.ws.user.exception.UserValidationException;
import com.bcra.users.ws.user.mapper.UserMapper;
import com.bcra.users.ws.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alta y consulta de clientes según las políticas configuradas. */
@Service
public class UserService {
    private final UserRepository repository;
    private final UserMapper mapper;
    private final AuditService audit;
    private final BankUsersProperties policy;

    public UserService(UserRepository repository, UserMapper mapper, AuditService audit, BankUsersProperties policy) {
        this.repository = repository;
        this.mapper = mapper;
        this.audit = audit;
        this.policy = policy;
    }

    /** Crea y audita un cliente en una sola transacción. */
    @Transactional
    public UserResponse create(CreateUserRequest request, String actor) {
        List<FieldError> errors = new ArrayList<>();
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
        if (repository.existsByUniquenessField(uniqueValue)) {
            throw new DuplicateUserException(policy.uniquenessField());
        }
        Instant now = Instant.now();
        User user = mapper.toEntity(request);
        user.setId(UUID.randomUUID());
        user.setExternalId(UUID.randomUUID().toString());
        user.setUniquenessField(uniqueValue);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            repository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMostSpecificCause().getMessage() != null
                    && exception.getMostSpecificCause().getMessage().toLowerCase(java.util.Locale.ROOT)
                            .contains("ux_users_uniqueness_field")) {
                throw new DuplicateUserException(policy.uniquenessField());
            }
            throw exception;
        }
        audit.registerCreation(user.getId(), actor == null || actor.isBlank() ? "system" : actor,
                AuditResult.OK, now);
        return filter(mapper.toResponse(user));
    }

    /** Consulta un cliente por su identificador interno. */
    @Transactional(readOnly = true)
    public UserResponse find(UUID id) {
        return filter(mapper.toResponse(repository.findById(id).orElseThrow(UserNotFoundException::new)));
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

    private UserResponse filter(UserResponse response) {
        List<String> fields = policy.publicFields();
        return new UserResponse(fields.contains("id") ? response.id() : null,
                fields.contains("externalId") ? response.externalId() : null,
                fields.contains("uniquenessValue") ? response.uniquenessValue() : null,
                fields.contains("displayName") ? response.displayName() : null,
                fields.contains("contactEmail") ? response.contactEmail() : null,
                fields.contains("contactPhone") ? response.contactPhone() : null, response.status(),
                fields.contains("createdAt") ? response.createdAt() : null);
    }
}
