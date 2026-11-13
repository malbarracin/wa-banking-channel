package com.bcra.ws.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.bcra.ws.users.exception.DuplicateDocumentException;
import com.bcra.ws.users.exception.InvalidStatusTransitionException;
import com.bcra.ws.users.exception.UserNotFoundException;
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
import com.bcra.ws.users.entity.UserAuditEventEntity;
import com.bcra.ws.users.entity.UserEntity;
import com.bcra.ws.users.repository.UserRepository;

/**
 * Unit tests for {@link UserServiceImpl} covering U1–U4 happy and error paths.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserAuditService userAuditService;

    @InjectMocks
    private UserServiceImpl subject;

    @Test
    @DisplayName("Should create user ACTIVE when document is unique")
    void shouldCreateActiveUser_whenDocumentIsUnique() {
        UserCreateRequest request = baseCreateRequest();
        UserEntity mapped = UserEntity.builder()
                .documentType(DocumentType.DNI)
                .documentNumber("30111222")
                .firstName("Maria")
                .lastName("Lopez")
                .email("maria.lopez@example.com")
                .build();
        UserEntity saved = copyWithId(mapped, "user-1", UserStatus.ACTIVE);
        UserResponse expected = UserResponse.builder()
                .id("user-1")
                .status(UserStatus.ACTIVE)
                .channelLinkEligible(true)
                .build();

        when(userRepository.existsByDocumentTypeAndDocumentNumber(DocumentType.DNI, "30111222"))
                .thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(mapped);
        when(userRepository.save(any(UserEntity.class))).thenReturn(saved);
        when(userMapper.toResponse(saved)).thenReturn(expected);

        UserResponse response = subject.create(request, "actor-1");

        assertThat(response.getId()).isEqualTo("user-1");
        assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.isChannelLinkEligible()).isTrue();
        verify(userAuditService).record(
                eq("user-1"),
                eq(AuditAction.USER_CREATED),
                eq(AuditResult.SUCCESS),
                eq(Map.of("status", "ACTIVE")),
                eq("actor-1"));
    }

    @Test
    @DisplayName("Should throw DuplicateDocumentException when document already exists")
    void shouldThrowDuplicate_whenDocumentAlreadyExists() {
        UserCreateRequest request = baseCreateRequest();
        when(userRepository.existsByDocumentTypeAndDocumentNumber(DocumentType.DNI, "30111222"))
                .thenReturn(true);

        assertThatThrownBy(() -> subject.create(request, null))
                .isInstanceOf(DuplicateDocumentException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
        verify(userAuditService).record(
                isNull(),
                eq(AuditAction.USER_CREATED),
                eq(AuditResult.FAILURE),
                any(),
                isNull());
    }

    @Test
    @DisplayName("Should throw DuplicateDocumentException when save hits unique index")
    void shouldThrowDuplicate_whenSaveRaisesDuplicateKey() {
        UserCreateRequest request = baseCreateRequest();
        UserEntity mapped = UserEntity.builder()
                .documentType(DocumentType.DNI)
                .documentNumber("30111222")
                .build();

        when(userRepository.existsByDocumentTypeAndDocumentNumber(DocumentType.DNI, "30111222"))
                .thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(mapped);
        when(userRepository.save(any(UserEntity.class)))
                .thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> subject.create(request, "SYSTEM"))
                .isInstanceOf(DuplicateDocumentException.class);

        verify(userAuditService).record(
                isNull(),
                eq(AuditAction.USER_CREATED),
                eq(AuditResult.FAILURE),
                any(),
                eq("SYSTEM"));
    }

    @Test
    @DisplayName("Should return user when id exists")
    void shouldReturnUser_whenIdExists() {
        UserEntity entity = sampleEntity("user-1", UserStatus.ACTIVE);
        UserResponse expected = UserResponse.builder().id("user-1").status(UserStatus.ACTIVE).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(entity));
        when(userMapper.toResponse(entity)).thenReturn(expected);

        UserResponse response = subject.findById("user-1");

        assertThat(response.getId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("Should throw UserNotFoundException when id is missing")
    void shouldThrowNotFound_whenIdMissing() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subject.findById("missing"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("Should find user by document when present")
    void shouldFindByDocument_whenPresent() {
        UserEntity entity = sampleEntity("user-1", UserStatus.ACTIVE);
        UserResponse expected = UserResponse.builder().id("user-1").build();
        when(userRepository.findByDocumentTypeAndDocumentNumber(DocumentType.DNI, "30111222"))
                .thenReturn(Optional.of(entity));
        when(userMapper.toResponse(entity)).thenReturn(expected);

        UserResponse response = subject.findByDocument(DocumentType.DNI, " 30111222 ");

        assertThat(response.getId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("Should throw when documentNumber is blank")
    void shouldThrowIllegalArgument_whenDocumentNumberBlank() {
        assertThatThrownBy(() -> subject.findByDocument(DocumentType.DNI, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("documentNumber");
    }

    @Test
    @DisplayName("Should throw not found when document does not exist")
    void shouldThrowNotFound_whenDocumentMissing() {
        when(userRepository.findByDocumentTypeAndDocumentNumber(DocumentType.DNI, "999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> subject.findByDocument(DocumentType.DNI, "999"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("Should update allowed fields and audit")
    void shouldUpdateAllowedFields_whenValidRequest() {
        UserEntity entity = sampleEntity("user-1", UserStatus.ACTIVE);
        UserUpdateRequest request = UserUpdateRequest.builder()
                .preferredName("Mari")
                .email("new@example.com")
                .phone("+549110000")
                .preferredLanguage("en")
                .build();
        UserEntity saved = sampleEntity("user-1", UserStatus.ACTIVE);
        saved.setPreferredName("Mari");
        saved.setEmail("new@example.com");
        UserResponse expected = UserResponse.builder().id("user-1").email("new@example.com").build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(entity));
        when(userRepository.save(any(UserEntity.class))).thenReturn(saved);
        when(userMapper.toResponse(saved)).thenReturn(expected);

        UserResponse response = subject.update("user-1", request, "actor-2");

        assertThat(response.getEmail()).isEqualTo("new@example.com");
        ArgumentCaptor<Map<String, Object>> summaryCaptor = ArgumentCaptor.forClass(Map.class);
        verify(userAuditService).record(
                eq("user-1"),
                eq(AuditAction.USER_UPDATED),
                eq(AuditResult.SUCCESS),
                summaryCaptor.capture(),
                eq("actor-2"));
        assertThat(summaryCaptor.getValue()).containsKeys("preferredName", "email", "phone", "preferredLanguage");
    }

    @Test
    @DisplayName("Should reject update when no fields present")
    void shouldThrowIllegalArgument_whenUpdateHasNoFields() {
        UserUpdateRequest request = UserUpdateRequest.builder().build();

        assertThatThrownBy(() -> subject.update("user-1", request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one updatable field");
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Should change status ACTIVE to SUSPENDED and audit")
    void shouldChangeStatus_whenTransitionAllowed() {
        UserEntity entity = sampleEntity("user-1", UserStatus.ACTIVE);
        UserEntity saved = sampleEntity("user-1", UserStatus.SUSPENDED);
        UserResponse expected = UserResponse.builder()
                .id("user-1")
                .status(UserStatus.SUSPENDED)
                .channelLinkEligible(false)
                .build();
        UserStatusChangeRequest request = UserStatusChangeRequest.builder()
                .status(UserStatus.SUSPENDED)
                .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(entity));
        when(userRepository.save(any(UserEntity.class))).thenReturn(saved);
        when(userMapper.toResponse(saved)).thenReturn(expected);

        UserResponse response = subject.changeStatus("user-1", request, "ops");

        assertThat(response.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(response.isChannelLinkEligible()).isFalse();
        verify(userAuditService).record(
                eq("user-1"),
                eq(AuditAction.STATUS_CHANGED),
                eq(AuditResult.SUCCESS),
                eq(Map.of("from", "ACTIVE", "to", "SUSPENDED")),
                eq("ops"));
    }

    @Test
    @DisplayName("Should reject invalid status transition and audit failure")
    void shouldRejectInvalidTransition_whenNotAllowed() {
        UserEntity entity = sampleEntity("user-1", UserStatus.ACTIVE);
        UserStatusChangeRequest request = UserStatusChangeRequest.builder()
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> subject.changeStatus("user-1", request, null))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(userRepository, never()).save(any());
        verify(userAuditService, never()).record(any(), eq(AuditAction.STATUS_CHANGED), any(), any(), any());
    }

    @Test
    @DisplayName("Should reject disallowed transition SUSPENDED to SUSPENDED path via LOGICALLY_DELETED reverse")
    void shouldRejectDisallowedTransition_andAuditFailure() {
        UserEntity entity = sampleEntity("user-1", UserStatus.LOGICALLY_DELETED);
        UserStatusChangeRequest request = UserStatusChangeRequest.builder()
                .status(UserStatus.SUSPENDED)
                .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> subject.changeStatus("user-1", request, "ops"))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(userAuditService).record(
                eq("user-1"),
                eq(AuditAction.STATUS_CHANGED),
                eq(AuditResult.FAILURE),
                eq(Map.of("from", "LOGICALLY_DELETED", "to", "SUSPENDED")),
                eq("ops"));
    }

    @Test
    @DisplayName("Should reactivate from LOGICALLY_DELETED to ACTIVE")
    void shouldReactivate_whenLogicallyDeletedToActive() {
        UserEntity entity = sampleEntity("user-1", UserStatus.LOGICALLY_DELETED);
        UserEntity saved = sampleEntity("user-1", UserStatus.ACTIVE);
        UserResponse expected = UserResponse.builder()
                .id("user-1")
                .status(UserStatus.ACTIVE)
                .channelLinkEligible(true)
                .build();
        UserStatusChangeRequest request = UserStatusChangeRequest.builder()
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(entity));
        when(userRepository.save(any(UserEntity.class))).thenReturn(saved);
        when(userMapper.toResponse(saved)).thenReturn(expected);

        UserResponse response = subject.changeStatus("user-1", request, null);

        assertThat(response.isChannelLinkEligible()).isTrue();
    }

    @Test
    @DisplayName("Should return audit trail when user exists")
    void shouldReturnAuditTrail_whenUserExists() {
        UserEntity entity = sampleEntity("user-1", UserStatus.ACTIVE);
        List<UserAuditEventEntity> events = List.of(
                UserAuditEventEntity.builder().id("a1").userId("user-1").action(AuditAction.USER_CREATED).build());
        List<UserAuditEventResponse> responses = List.of(
                UserAuditEventResponse.builder().id("a1").userId("user-1").action(AuditAction.USER_CREATED).build());

        when(userRepository.findById("user-1")).thenReturn(Optional.of(entity));
        when(userAuditService.findByUserId("user-1")).thenReturn(events);
        when(userMapper.toAuditResponseList(events)).thenReturn(responses);

        List<UserAuditEventResponse> result = subject.findAuditTrail("user-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAction()).isEqualTo(AuditAction.USER_CREATED);
    }

    @Test
    @DisplayName("Should throw when id is blank")
    void shouldThrowIllegalArgument_whenIdBlank() {
        assertThatThrownBy(() -> subject.findById("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id is required");
    }

    @Test
    @DisplayName("Should default preferredLanguage to es when blank on create")
    void shouldDefaultLanguage_whenPreferredLanguageBlank() {
        UserCreateRequest request = baseCreateRequest();
        request.setPreferredLanguage("  ");
        UserEntity mapped = UserEntity.builder().documentType(DocumentType.DNI).documentNumber("30111222").build();
        UserEntity saved = copyWithId(mapped, "user-1", UserStatus.ACTIVE);
        UserResponse expected = UserResponse.builder().id("user-1").preferredLanguage("es").build();

        when(userRepository.existsByDocumentTypeAndDocumentNumber(DocumentType.DNI, "30111222"))
                .thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(mapped);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity arg = invocation.getArgument(0);
            assertThat(arg.getPreferredLanguage()).isEqualTo("es");
            return saved;
        });
        when(userMapper.toResponse(saved)).thenReturn(expected);

        subject.create(request, null);
    }

    private static UserCreateRequest baseCreateRequest() {
        return UserCreateRequest.builder()
                .documentType(DocumentType.DNI)
                .documentNumber("30111222")
                .firstName("Maria")
                .lastName("Lopez")
                .email("maria.lopez@example.com")
                .build();
    }

    private static UserEntity sampleEntity(String id, UserStatus status) {
        Instant now = Instant.parse("2026-10-19T12:00:00Z");
        return UserEntity.builder()
                .id(id)
                .documentType(DocumentType.DNI)
                .documentNumber("30111222")
                .firstName("Maria")
                .lastName("Lopez")
                .email("maria.lopez@example.com")
                .preferredLanguage("es")
                .status(status)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static UserEntity copyWithId(UserEntity source, String id, UserStatus status) {
        Instant now = Instant.parse("2026-10-19T12:00:00Z");
        return UserEntity.builder()
                .id(id)
                .documentType(source.getDocumentType())
                .documentNumber(source.getDocumentNumber())
                .firstName(source.getFirstName())
                .lastName(source.getLastName())
                .email(source.getEmail())
                .preferredLanguage("es")
                .status(status)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
