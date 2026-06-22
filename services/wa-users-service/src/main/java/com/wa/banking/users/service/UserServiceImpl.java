package com.wa.banking.users.service;

import com.wa.banking.users.api.v1.dto.ChangeUserStatusRequestV1;
import com.wa.banking.users.api.v1.dto.CreateUserRequestV1;
import com.wa.banking.users.api.v1.dto.UpdateUserRequestV1;
import com.wa.banking.users.api.v1.dto.UserAuditResponseV1;
import com.wa.banking.users.api.v1.dto.UserResponseV1;
import com.wa.banking.users.entity.AuditAction;
import com.wa.banking.users.entity.BankUserEntity;
import com.wa.banking.users.entity.DocumentType;
import com.wa.banking.users.entity.UserAuditEntryEntity;
import com.wa.banking.users.entity.UserStatus;
import com.wa.banking.users.exception.DuplicateDocumentException;
import com.wa.banking.users.exception.UserNotFoundException;
import com.wa.banking.users.mapper.UserMapperV1;
import com.wa.banking.users.repository.BankUserRepository;
import com.wa.banking.users.repository.UserAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Implementación del servicio de usuarios bancarios con reglas de negocio H1.
 *
 * @author licius-it
 * @since 2026-06-22
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String AUDIT_RESULT_SUCCESS = "SUCCESS";

    private final BankUserRepository bankUserRepository;
    private final UserAuditRepository userAuditRepository;
    private final UserMapperV1 userMapperV1;
    private final StatusTransitionPolicy statusTransitionPolicy;

    @Override
    public UserResponseV1 create(CreateUserRequestV1 request) {
        log.info("Creating bank user for documentType={}", request.documentType());

        if (bankUserRepository.existsByDocumentTypeAndDocumentNumber(
                request.documentType(), request.documentNumber())) {
            throw new DuplicateDocumentException(
                    "User already exists with document " + request.documentType() + " "
                            + request.documentNumber());
        }

        Instant now = Instant.now();
        BankUserEntity entity = userMapperV1.toEntity(request);
        entity.setStatus(UserStatus.ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        BankUserEntity saved = bankUserRepository.save(entity);
        saveAuditEntry(saved.getId(), AuditAction.CREATED, null, UserStatus.ACTIVE, List.of());

        log.info("Bank user created with id={}", saved.getId());
        return userMapperV1.toResponse(saved);
    }

    @Override
    public UserResponseV1 findById(String id) {
        log.info("Finding bank user by id={}", id);
        BankUserEntity entity = findEntityById(id);
        return userMapperV1.toResponse(entity);
    }

    @Override
    public UserResponseV1 findByDocument(DocumentType documentType, String documentNumber) {
        log.info("Finding bank user by documentType={}", documentType);
        BankUserEntity entity = bankUserRepository
                .findByDocumentTypeAndDocumentNumber(documentType, documentNumber)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with document " + documentType + " " + documentNumber));
        return userMapperV1.toResponse(entity);
    }

    @Override
    public UserResponseV1 update(String id, UpdateUserRequestV1 request) {
        log.info("Updating bank user id={}", id);
        BankUserEntity entity = findEntityById(id);
        List<String> changedFields = applyMutableFields(entity, request);

        if (changedFields.isEmpty()) {
            return userMapperV1.toResponse(entity);
        }

        entity.setUpdatedAt(Instant.now());
        BankUserEntity saved = bankUserRepository.save(entity);
        saveAuditEntry(saved.getId(), AuditAction.UPDATED, null, null, changedFields);

        log.info("Bank user updated id={}, changedFields={}", saved.getId(), changedFields);
        return userMapperV1.toResponse(saved);
    }

    @Override
    public UserResponseV1 changeStatus(String id, ChangeUserStatusRequestV1 request) {
        log.info("Changing status for bank user id={}", id);
        BankUserEntity entity = findEntityById(id);
        UserStatus previousStatus = entity.getStatus();
        UserStatus targetStatus = request.status();

        statusTransitionPolicy.validateTransition(previousStatus, targetStatus);

        entity.setStatus(targetStatus);
        entity.setUpdatedAt(Instant.now());
        BankUserEntity saved = bankUserRepository.save(entity);

        saveAuditEntry(saved.getId(), AuditAction.STATUS_CHANGED, previousStatus, targetStatus, List.of());

        log.info("Bank user status changed id={}, from={}, to={}", id, previousStatus, targetStatus);
        return userMapperV1.toResponse(saved);
    }

    @Override
    public Page<UserAuditResponseV1> getAuditHistory(String id, Pageable pageable) {
        log.info("Fetching audit history for bank user id={}", id);
        if (!bankUserRepository.existsById(id)) {
            throw new UserNotFoundException("User not found with id " + id);
        }

        return userAuditRepository.findByUserIdOrderByPerformedAtDesc(id, pageable)
                .map(userMapperV1::toAuditResponse);
    }

    private BankUserEntity findEntityById(String id) {
        return bankUserRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id " + id));
    }

    private List<String> applyMutableFields(BankUserEntity entity, UpdateUserRequestV1 request) {
        List<String> changedFields = new ArrayList<>();

        if (request.displayName() != null && !Objects.equals(entity.getDisplayName(), request.displayName())) {
            entity.setDisplayName(request.displayName());
            changedFields.add("displayName");
        }
        if (request.email() != null && !Objects.equals(entity.getEmail(), request.email())) {
            entity.setEmail(request.email());
            changedFields.add("email");
        }
        if (request.phone() != null && !Objects.equals(entity.getPhone(), request.phone())) {
            entity.setPhone(request.phone());
            changedFields.add("phone");
        }
        if (request.preferences() != null && !Objects.equals(entity.getPreferences(), request.preferences())) {
            entity.setPreferences(request.preferences());
            changedFields.add("preferences");
        }

        return changedFields;
    }

    private void saveAuditEntry(
            String userId,
            AuditAction action,
            UserStatus previousStatus,
            UserStatus newStatus,
            List<String> changedFields) {
        UserAuditEntryEntity auditEntry = UserAuditEntryEntity.builder()
                .userId(userId)
                .action(action)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .changedFields(changedFields)
                .performedAt(Instant.now())
                .result(AUDIT_RESULT_SUCCESS)
                .build();
        userAuditRepository.save(auditEntry);
    }
}
