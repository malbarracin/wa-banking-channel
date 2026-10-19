package com.bank.users.user.service;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import com.bank.users.audit.service.AuditService;
import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.user.dto.ChangeStatusRequest;
import com.bank.users.user.dto.UpdateUserRequest;
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.exception.InvalidUserUpdateException;
import com.bank.users.user.exception.InvalidStatusTransitionException;
import com.bank.users.user.exception.UserUpdateConflictException;
import com.bank.users.user.exception.DuplicateUserException;
import com.bank.users.user.exception.UserNotFoundException;
import com.bank.users.user.mapper.UserMapper;
import com.bank.users.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** Aplica altas, actualizaciones auditadas y consultas de datos autorizados del usuario. */
@Service
@Validated
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AuditService auditService;

    /** Cambia el estado permitido y registra su resultado en la misma transacción. */
    @Transactional
    public UserResponse changeStatus(@NotNull UUID id, @NotNull @Valid ChangeStatusRequest request) {
        User user = userRepository.findById(id).orElseThrow(UserNotFoundException::new);
        boolean permitted = switch (user.getStatus()) {
            case ACTIVE -> request.status() == UserStatus.SUSPENDED
                    || request.status() == UserStatus.LOGICALLY_DELETED;
            case SUSPENDED -> request.status() == UserStatus.ACTIVE
                    || request.status() == UserStatus.LOGICALLY_DELETED;
            case LOGICALLY_DELETED -> false;
        };
        if (!permitted) {
            throw new InvalidStatusTransitionException();
        }
        user.setStatus(request.status());
        userRepository.saveAndFlush(user);
        auditService.recordStatusChanged(id, user.getStatus().name());
        return userMapper.toResponse(user);
    }

    /** Aplica solo campos presentes y confirma cambios relevantes y auditoría en una transacción. */
    @Transactional
    public UserResponse update(@NotNull UUID id, @NotNull @Valid UpdateUserRequest request) {
        User user = userRepository.findById(id).orElseThrow(UserNotFoundException::new);
        if (user.getStatus() == UserStatus.LOGICALLY_DELETED) {
            throw new UserUpdateConflictException();
        }
        String email = request.hasField("email") ? request.getEmail() : user.getEmail();
        String phone = request.hasField("phone") ? request.getPhone() : user.getPhone();
        if ((email == null || email.isBlank()) && (phone == null || phone.isBlank())) {
            throw new InvalidUserUpdateException("contact", "Debe indicar al menos email o teléfono");
        }
        Set<String> changedFields = new LinkedHashSet<>();
        if (request.hasField("displayName") && !Objects.equals(user.getDisplayName(), request.getDisplayName())) {
            changedFields.add("displayName");
        }
        if (!Objects.equals(user.getEmail(), email)) {
            changedFields.add("email");
        }
        if (!Objects.equals(user.getPhone(), phone)) {
            changedFields.add("phone");
        }
        if (request.hasField("preferences") && !Objects.equals(user.getPreferences(), request.getPreferences())) {
            changedFields.add("preferences");
        }
        if (!changedFields.isEmpty()) {
            userMapper.update(request, user);
            userRepository.saveAndFlush(user);
            auditService.recordUserUpdated(id, changedFields);
        }
        return userMapper.toResponse(user);
    }

    /** Consulta cualquier estado operativo por ID o informa que no existe el usuario. */
    @Transactional(readOnly = true)
    public UserResponse findById(@NotNull UUID id) {
        return userRepository.findById(id).map(userMapper::toResponse).orElseThrow(UserNotFoundException::new);
    }

    /** Consulta por la clave documental completa sin revelar el documento en errores. */
    @Transactional(readOnly = true)
    public UserResponse findByDocument(@NotNull DocumentType documentType,
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9]+") String documentNumber) {
        return userRepository.findByDocumentTypeAndDocumentNumber(documentType, documentNumber)
                .map(userMapper::toResponse).orElseThrow(UserNotFoundException::new);
    }

    /** Crea un usuario activo o rechaza el documento duplicado sin revelar sus valores. */
    @Transactional
    public UserResponse create(@NotNull @Valid CreateUserRequest request) {
        if (userRepository.existsByDocumentTypeAndDocumentNumber(
                DocumentType.valueOf(request.documentType()), request.documentNumber())) {
            throw new DuplicateUserException();
        }
        User user = userMapper.toEntity(request);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())) {
                throw new DuplicateUserException();
            }
            throw exception;
        }
        auditService.recordUserCreated(user.getId());
        return userMapper.toResponse(user);
    }
}
