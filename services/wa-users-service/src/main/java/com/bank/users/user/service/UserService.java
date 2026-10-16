package com.bank.users.user.service;

import java.util.Objects;
import java.util.Locale;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bank.users.audit.entity.AuditAction;
import com.bank.users.audit.entity.AuditDetail;
import com.bank.users.audit.service.AuditService;
import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.entity.User;
import com.bank.users.user.exception.DuplicateUserException;
import com.bank.users.user.exception.UserNotFoundException;
import com.bank.users.user.mapper.UserMapper;
import com.bank.users.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** Alta con unicidad y auditoría; el rechazo se registra después del rollback de la operación. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final UserMapper mapper;
    private final AuditService auditService;
    private final PlatformTransactionManager transactionManager;

    /** Consulta por UUID con historial resumido, también para usuarios suspendidos o en baja lógica. */
    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        User user = repository.findById(id).orElseThrow(UserNotFoundException::new);
        return mapper.toResponse(user, auditService.findHistory(id));
    }

    /** Crea un usuario activo y confirma usuario y auditoría OK en la misma transacción. */
    public UserResponse create(CreateUserRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            return Objects.requireNonNull(transaction.execute(status -> {
                if (repository.existsByTipoDocumentoAndNumeroDocumento(
                        request.tipoDocumento(), request.numeroDocumento())) {
                    throw new DuplicateUserException();
                }
                User user = repository.saveAndFlush(mapper.toEntity(request));
                auditService.recordSuccess(user.getId(), AuditAction.ALTA);
                return mapper.toResponse(user);
            }));
        } catch (DuplicateUserException exception) {
            auditService.recordRejection(null, AuditAction.ALTA, AuditDetail.USUARIO_DUPLICADO);
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            if (!isDocumentCollision(exception)) {
                throw exception;
            }
            auditService.recordRejection(null, AuditAction.ALTA, AuditDetail.USUARIO_DUPLICADO);
            throw new DuplicateUserException(exception);
        }
    }

    private boolean isDocumentCollision(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                String constraint = violation.getConstraintName().toLowerCase(Locale.ROOT);
                String name = constraint.substring(constraint.lastIndexOf('.') + 1);
                if (name.equals("uk_users_documento") || name.startsWith("uk_users_documento_index_")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }
}
