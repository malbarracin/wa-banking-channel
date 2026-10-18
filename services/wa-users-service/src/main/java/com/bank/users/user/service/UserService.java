package com.bank.users.user.service;

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
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;
import com.bank.users.user.exception.DuplicateUserException;
import com.bank.users.user.exception.UserNotFoundException;
import com.bank.users.user.mapper.UserMapper;
import com.bank.users.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** Aplica el alta auditada y las consultas de datos autorizados del usuario. */
@Service
@Validated
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AuditService auditService;

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
