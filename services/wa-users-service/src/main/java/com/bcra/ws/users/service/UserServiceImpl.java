package com.bcra.ws.users.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.bcra.ws.users.api.error.DuplicateDocumentException;
import com.bcra.ws.users.api.error.InvalidStatusTransitionException;
import com.bcra.ws.users.api.error.UserNotFoundException;
import com.bcra.ws.users.api.v1.dto.UserAuditEventResponse;
import com.bcra.ws.users.api.v1.dto.UserCreateRequest;
import com.bcra.ws.users.api.v1.dto.UserResponse;
import com.bcra.ws.users.api.v1.dto.UserStatusChangeRequest;
import com.bcra.ws.users.api.v1.dto.UserUpdateRequest;
import com.bcra.ws.users.api.v1.mapper.UserMapper;
import com.bcra.ws.users.domain.AuditAction;
import com.bcra.ws.users.domain.AuditResult;
import com.bcra.ws.users.domain.DocumentType;
import com.bcra.ws.users.domain.UserStatus;
import com.bcra.ws.users.entity.UserEntity;
import com.bcra.ws.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementación de U1–U4: alta, consulta, actualización y cambio de estado con auditoría.
 *
 * @since 2026-10-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String DEFAULT_LANGUAGE = "es";

    private static final Map<UserStatus, Set<UserStatus>> ALLOWED_TRANSITIONS = Map.of(
            UserStatus.ACTIVE, Set.of(UserStatus.SUSPENDED, UserStatus.LOGICALLY_DELETED),
            UserStatus.SUSPENDED, Set.of(UserStatus.ACTIVE, UserStatus.LOGICALLY_DELETED),
            UserStatus.LOGICALLY_DELETED, Set.of(UserStatus.ACTIVE)
    );

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserAuditService userAuditService;

    @Override
    public UserResponse create(UserCreateRequest request, String actor) {
        Objects.requireNonNull(request, "request must not be null");
        String documentNumber = normalizeDocumentNumber(request.getDocumentNumber());
        DocumentType documentType = request.getDocumentType();

        if (userRepository.existsByDocumentTypeAndDocumentNumber(documentType, documentNumber)) {
            userAuditService.record(
                    null,
                    AuditAction.USER_CREATED,
                    AuditResult.FAILURE,
                    Map.of("documentType", documentType.name(), "documentNumber", documentNumber, "reason", "DUPLICATE"),
                    actor);
            throw new DuplicateDocumentException(documentType, documentNumber);
        }

        Instant now = Instant.now();
        UserEntity entity = userMapper.toEntity(request);
        entity.setDocumentNumber(documentNumber);
        entity.setPreferredLanguage(resolveLanguage(request.getPreferredLanguage()));
        entity.setStatus(UserStatus.ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        try {
            UserEntity saved = userRepository.save(entity);
            userAuditService.record(
                    saved.getId(),
                    AuditAction.USER_CREATED,
                    AuditResult.SUCCESS,
                    Map.of("status", UserStatus.ACTIVE.name()),
                    actor);
            log.info("User created: id={}", saved.getId());
            return userMapper.toResponse(saved);
        } catch (DuplicateKeyException exception) {
            userAuditService.record(
                    null,
                    AuditAction.USER_CREATED,
                    AuditResult.FAILURE,
                    Map.of("documentType", documentType.name(), "documentNumber", documentNumber, "reason", "DUPLICATE"),
                    actor);
            throw new DuplicateDocumentException(documentType, documentNumber);
        }
    }

    @Override
    public UserResponse findById(String id) {
        return userMapper.toResponse(requireUser(id));
    }

    @Override
    public UserResponse findByDocument(DocumentType documentType, String documentNumber) {
        Objects.requireNonNull(documentType, "documentType must not be null");
        String normalized = normalizeDocumentNumber(documentNumber);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("documentNumber is required");
        }
        UserEntity entity = userRepository.findByDocumentTypeAndDocumentNumber(documentType, normalized)
                .orElseThrow(() -> UserNotFoundException.byDocument(documentType.name(), normalized));
        return userMapper.toResponse(entity);
    }

    @Override
    public UserResponse update(String id, UserUpdateRequest request, String actor) {
        Objects.requireNonNull(request, "request must not be null");
        validateAtLeastOneUpdateField(request);

        UserEntity entity = requireUser(id);
        Map<String, Object> changed = new HashMap<>();

        if (request.getPreferredName() != null) {
            entity.setPreferredName(request.getPreferredName());
            changed.put("preferredName", request.getPreferredName());
        }
        if (request.getEmail() != null) {
            entity.setEmail(request.getEmail());
            changed.put("email", request.getEmail());
        }
        if (request.getPhone() != null) {
            entity.setPhone(request.getPhone());
            changed.put("phone", request.getPhone());
        }
        if (request.getPreferredLanguage() != null) {
            String language = resolveLanguage(request.getPreferredLanguage());
            entity.setPreferredLanguage(language);
            changed.put("preferredLanguage", language);
        }

        entity.setUpdatedAt(Instant.now());
        UserEntity saved = userRepository.save(entity);
        userAuditService.record(saved.getId(), AuditAction.USER_UPDATED, AuditResult.SUCCESS, changed, actor);
        log.info("User updated: id={}, fields={}", id, changed.keySet());
        return userMapper.toResponse(saved);
    }

    @Override
    public UserResponse changeStatus(String id, UserStatusChangeRequest request, String actor) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.getStatus(), "status must not be null");

        UserEntity entity = requireUser(id);
        UserStatus from = entity.getStatus();
        UserStatus to = request.getStatus();

        if (from == to) {
            throw new InvalidStatusTransitionException(from, to);
        }
        if (!isTransitionAllowed(from, to)) {
            userAuditService.record(
                    id,
                    AuditAction.STATUS_CHANGED,
                    AuditResult.FAILURE,
                    Map.of("from", from.name(), "to", to.name()),
                    actor);
            throw new InvalidStatusTransitionException(from, to);
        }

        entity.setStatus(to);
        entity.setUpdatedAt(Instant.now());
        UserEntity saved = userRepository.save(entity);
        userAuditService.record(
                saved.getId(),
                AuditAction.STATUS_CHANGED,
                AuditResult.SUCCESS,
                Map.of("from", from.name(), "to", to.name()),
                actor);
        log.info("User status changed: id={}, from={}, to={}", id, from, to);
        return userMapper.toResponse(saved);
    }

    @Override
    public List<UserAuditEventResponse> findAuditTrail(String id) {
        requireUser(id);
        return userMapper.toAuditResponseList(userAuditService.findByUserId(id));
    }

    private UserEntity requireUser(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        return userRepository.findById(id.trim())
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    private static boolean isTransitionAllowed(UserStatus from, UserStatus to) {
        Set<UserStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    private static void validateAtLeastOneUpdateField(UserUpdateRequest request) {
        boolean anyPresent = request.getPreferredName() != null
                || request.getEmail() != null
                || request.getPhone() != null
                || request.getPreferredLanguage() != null;
        if (!anyPresent) {
            throw new IllegalArgumentException(
                    "At least one updatable field is required: preferredName, email, phone, preferredLanguage");
        }
    }

    private static String normalizeDocumentNumber(String documentNumber) {
        if (documentNumber == null) {
            return "";
        }
        return documentNumber.trim();
    }

    private static String resolveLanguage(String preferredLanguage) {
        if (preferredLanguage == null || preferredLanguage.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        return preferredLanguage.trim();
    }
}
