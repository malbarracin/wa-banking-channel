package com.bank.users.user.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.bank.users.audit.service.AuditService;
import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;
import com.bank.users.user.exception.DuplicateUserException;
import com.bank.users.user.mapper.UserMapper;
import com.bank.users.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** Aplica el alta y su auditoría en una misma transacción. */
@Service
@Validated
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AuditService auditService;

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
