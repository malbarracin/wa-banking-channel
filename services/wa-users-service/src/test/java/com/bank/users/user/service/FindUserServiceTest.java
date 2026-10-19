package com.bank.users.user.service;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bank.users.audit.service.AuditService;
import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.exception.UserNotFoundException;
import com.bank.users.user.mapper.UserMapperImpl;
import com.bank.users.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FindUserServiceTest {

    @Mock
    private UserRepository repository;

    @Mock
    private AuditService auditService;

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("Las consultas por ID y documento preservan el estado y no generan auditoría")
    void findsBothIdentifiers(UserStatus status) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setStatus(status);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        when(repository.findByDocumentTypeAndDocumentNumber(DocumentType.DNI, "123"))
                .thenReturn(Optional.of(user));
        UserService service = new UserService(repository, new UserMapperImpl(), auditService);

        var response = service.findById(user.getId());

        assertThat(service.findByDocument(DocumentType.DNI, "123")).isEqualTo(response);
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.channelLinkEligible()).isEqualTo(status == UserStatus.ACTIVE);
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("Ambas búsquedas ausentes lanzan la excepción de dominio")
    void rejectsMissingUsers() {
        UserService service = new UserService(repository, new UserMapperImpl(), auditService);

        assertThatThrownBy(() -> service.findById(UUID.randomUUID())).isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> service.findByDocument(DocumentType.DNI, "123"))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(auditService);
    }
}
