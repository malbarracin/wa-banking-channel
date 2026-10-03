package com.bank.users.user.service;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.repository.AuditEventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AuditEventService — atomicidad y entradas inválidas")
class AuditEventTransactionTest {

    private static final Long USER_ID = 42L;

    @Autowired
    private AuditEventService service;

    @Autowired
    private AuditEventRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("Revierte los eventos de cambio de estado y datos cuando falla el caso de uso")
    void rollsBackSuccessfulChangesWithCaller() {
        // Given / When
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            service.recordStatusChanged(USER_ID, UserStatus.ACTIVE, UserStatus.SUSPENDED);
            service.recordDataModified(USER_ID, List.of("email"));
            repository.flush();
            throw new IllegalStateException("operación revertida");
        })).isInstanceOf(IllegalStateException.class);

        // Then
        assertThat(repository.count()).isZero();
        assertThat(service.findByUser(USER_ID)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "12345678", "Juan Pérez", "email=juan@example.com", "{\"email\":\"x\"}", "email,displayName"
    })
    @DisplayName("Rechaza listas con un valor inválido sin guardar parcialmente los campos válidos")
    void rejectsInvalidFieldWithoutPartialAudit(String invalidField) {
        // Given / When
        assertThatThrownBy(() -> service.recordDataModified(USER_ID, Arrays.asList("email", invalidField)))
                .isInstanceOf(IllegalArgumentException.class);

        // Then
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("Rechaza estados, identificadores y motivos ausentes sin persistir eventos incompletos")
    void rejectsMissingMetadata() {
        // When / Then
        assertThatThrownBy(() -> service.recordStatusChanged(null, UserStatus.ACTIVE, UserStatus.SUSPENDED))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.recordStatusChanged(USER_ID, null, UserStatus.SUSPENDED))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.recordStatusChanged(USER_ID, UserStatus.ACTIVE, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.recordDataModified(null, List.of("email")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.recordCreationRejected(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.findByUser(null))
                .isInstanceOf(NullPointerException.class);
        assertThat(repository.count()).isZero();
    }
}
