package com.wa.banking.users.service;

import com.wa.banking.users.api.v1.dto.ChangeUserStatusRequestV1;
import com.wa.banking.users.api.v1.dto.CreateUserRequestV1;
import com.wa.banking.users.api.v1.dto.UpdateUserRequestV1;
import com.wa.banking.users.api.v1.dto.UserResponseV1;
import com.wa.banking.users.entity.BankUserEntity;
import com.wa.banking.users.entity.DocumentType;
import com.wa.banking.users.entity.UserStatus;
import com.wa.banking.users.exception.DuplicateDocumentException;
import com.wa.banking.users.exception.InvalidStatusTransitionException;
import com.wa.banking.users.exception.UserNotFoundException;
import com.wa.banking.users.mapper.UserMapperV1;
import com.wa.banking.users.repository.BankUserRepository;
import com.wa.banking.users.repository.UserAuditRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios para reglas de negocio del servicio de usuarios.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private BankUserRepository bankUserRepository;

    @Mock
    private UserAuditRepository userAuditRepository;

    @Mock
    private UserMapperV1 userMapperV1;

    @Mock
    private StatusTransitionPolicy statusTransitionPolicy;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    @DisplayName("Should throw DuplicateDocumentException when document already exists")
    void shouldThrowException_whenDuplicateDocumentOnCreate() {
        CreateUserRequestV1 request = new CreateUserRequestV1(
                DocumentType.DNI,
                "12345678",
                "John Doe",
                "john@example.com",
                "+541112345678",
                Map.of()
        );

        when(bankUserRepository.existsByDocumentTypeAndDocumentNumber(DocumentType.DNI, "12345678"))
                .thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(DuplicateDocumentException.class)
                .hasMessageContaining("User already exists with document DNI 12345678");

        verify(bankUserRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw UserNotFoundException when user id does not exist")
    void shouldThrowException_whenUserNotFoundById() {
        when(bankUserRepository.findById("missing-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById("missing-id"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found with id missing-id");
    }

    @Test
    @DisplayName("Should throw InvalidStatusTransitionException when transition is not allowed")
    void shouldThrowException_whenInvalidStatusTransition() {
        BankUserEntity entity = buildEntity(UserStatus.SOFT_DELETED);
        ChangeUserStatusRequestV1 request = new ChangeUserStatusRequestV1(UserStatus.ACTIVE);

        when(bankUserRepository.findById("user-1")).thenReturn(Optional.of(entity));
        doThrow(new InvalidStatusTransitionException("Transition from SOFT_DELETED to ACTIVE is not allowed"))
                .when(statusTransitionPolicy)
                .validateTransition(UserStatus.SOFT_DELETED, UserStatus.ACTIVE);

        assertThatThrownBy(() -> userService.changeStatus("user-1", request))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(bankUserRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return current user when update has no mutable changes")
    void shouldReturnUnchangedUser_whenUpdateHasNoChanges() {
        BankUserEntity entity = buildEntity(UserStatus.ACTIVE);
        UpdateUserRequestV1 request = new UpdateUserRequestV1(
                entity.getDisplayName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getPreferences()
        );
        UserResponseV1 response = new UserResponseV1(
                entity.getId(),
                entity.getDocumentType(),
                entity.getDocumentNumber(),
                entity.getDisplayName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getPreferences(),
                entity.getStatus(),
                true,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );

        when(bankUserRepository.findById("user-1")).thenReturn(Optional.of(entity));
        when(userMapperV1.toResponse(entity)).thenReturn(response);

        UserResponseV1 result = userService.update("user-1", request);

        assertThat(result).isEqualTo(response);
        verify(bankUserRepository, never()).save(any());
        verify(userAuditRepository, never()).save(any());
    }

    private BankUserEntity buildEntity(UserStatus status) {
        Instant now = Instant.parse("2026-06-22T12:00:00Z");
        return BankUserEntity.builder()
                .id("user-1")
                .documentType(DocumentType.DNI)
                .documentNumber("12345678")
                .displayName("John Doe")
                .email("john@example.com")
                .phone("+541112345678")
                .preferences(Map.of("lang", "es"))
                .status(status)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
