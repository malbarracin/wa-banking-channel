package com.bank.users.user.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.bank.users.audit.service.AuditService;
import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.exception.DuplicateUserException;
import com.bank.users.user.mapper.UserMapper;
import com.bank.users.user.mapper.UserMapperImpl;
import com.bank.users.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repository;

    @Mock
    private AuditService auditService;

    private final UserMapper mapper = new UserMapperImpl();
    private final CreateUserRequest request = new CreateUserRequest("DNI", "12345678", "Ana",
            "ana@example.com", null, LocalDate.of(1990, 1, 2));

    @Test
    @DisplayName("El alta asigna ACTIVE y registra auditoría antes de devolver el DTO")
    void createsAndAuditsActiveUser() {
        UUID id = UUID.randomUUID();
        when(repository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(id);
            return user;
        });

        var response = new UserService(repository, mapper, auditService).create(request);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.channelLinkEligible()).isTrue();
        verify(auditService).recordUserCreated(id);
        assertThat(request.toString()).doesNotContain("12345678", "ana@example.com");
        assertThat(response.toString()).doesNotContain("12345678", "ana@example.com");
    }

    @Test
    @DisplayName("El duplicado detectado antes del insert no genera auditoría")
    void rejectsExistingDocument() {
        when(repository.existsByDocumentTypeAndDocumentNumber(DocumentType.DNI, "12345678")).thenReturn(true);

        assertThatThrownBy(() -> new UserService(repository, mapper, auditService).create(request))
                .isInstanceOf(DuplicateUserException.class);
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("La violación de unicidad concurrente se convierte en conflicto")
    void translatesConcurrentDuplicate() {
        ConstraintViolationException cause = new ConstraintViolationException("constraint",
                new java.sql.SQLException("duplicate", "23505"), "uk_users_document");
        when(repository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("db", cause));

        assertThatThrownBy(() -> new UserService(repository, mapper, auditService).create(request))
                .isInstanceOf(DuplicateUserException.class);
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("Otros errores de integridad no se presentan como documento duplicado")
    void preservesOtherIntegrityFailure() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("other constraint");
        when(repository.saveAndFlush(any(User.class))).thenThrow(failure);

        assertThatThrownBy(() -> new UserService(repository, mapper, auditService).create(request)).isSameAs(failure);
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("Una restricción distinta de unicidad conserva el error de integridad")
    void preservesNonUniqueConstraintFailure() {
        ConstraintViolationException cause = new ConstraintViolationException("constraint",
                new java.sql.SQLException("not null", "23502"), "required_field");
        DataIntegrityViolationException failure = new DataIntegrityViolationException("db", cause);
        when(repository.saveAndFlush(any(User.class))).thenThrow(failure);

        assertThatThrownBy(() -> new UserService(repository, mapper, auditService).create(request)).isSameAs(failure);
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("Un error de auditoría se propaga para revertir la transacción")
    void propagatesAuditFailure() {
        doThrow(new IllegalStateException("audit unavailable")).when(auditService).recordUserCreated(null);

        assertThatThrownBy(() -> new UserService(repository, mapper, auditService).create(request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("MapStruct deriva aptitud del estado y devuelve preferencias inmodificables")
    void derivesEligibilityAndProtectsPreferences() {
        User user = mapper.toEntity(request);
        user.setPreferences(Map.of("language", "es"));
        user.setStatus(UserStatus.SUSPENDED);
        assertThat(mapper.toResponse(user).channelLinkEligible()).isFalse();
        user.setStatus(UserStatus.LOGICALLY_DELETED);
        assertThat(mapper.toResponse(user).channelLinkEligible()).isFalse();
        assertThatThrownBy(() -> mapper.toResponse(user).preferences().put("language", "en"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(user.toString()).doesNotContain("12345678", "ana@example.com");
    }
}
