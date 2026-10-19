package com.bank.users.audit.service;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bank.users.audit.entity.AuditAction;
import com.bank.users.audit.entity.AuditLog;
import com.bank.users.audit.repository.AuditLogRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@Import(AuditService.class)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuditTransactionIntegrationTest {

    private final AuditLogRepository repository;
    private final AuditService service;
    private final TransactionTemplate transaction;

    AuditTransactionIntegrationTest(AuditLogRepository repository, AuditService service,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.service = service;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Test
    @DisplayName("Las tres acciones quedan disponibles en otra transacción después del commit")
    void retainsAllActionsAfterCallingTransactionCommits() {
        UUID userId = UUID.randomUUID();

        try {
            transaction.executeWithoutResult(status -> {
                service.recordUserCreated(userId);
                service.recordUserUpdated(userId, Set.of("phone", "email"));
                service.recordStatusChanged(userId, "ACTIVE");
            });

            transaction.executeWithoutResult(status -> {
                assertThat(repository.findAll().stream()
                        .filter(entry -> userId.equals(entry.getUserId())).toList())
                        .hasSize(3)
                        .allSatisfy(entry -> {
                            assertThat(entry.getId()).isNotNull();
                            assertThat(entry.getOccurredAt()).isNotNull();
                            assertThat(entry.getResult()).isEqualTo("SUCCESS");
                        })
                        .extracting(AuditLog::getAction, AuditLog::getDetail)
                        .containsExactlyInAnyOrder(
                                tuple(AuditAction.USER_CREATED, "status=ACTIVE"),
                                tuple(AuditAction.USER_UPDATED, "fields=email,phone"),
                                tuple(AuditAction.STATUS_CHANGED, "status=ACTIVE"));
            });
        } finally {
            transaction.executeWithoutResult(status -> repository.deleteAll(repository.findAll().stream()
                    .filter(entry -> userId.equals(entry.getUserId())).toList()));
        }
    }

    @Test
    @DisplayName("El rollback del caso de uso revierte las tres acciones después de hacer flush")
    void rollsBackAllActionsAfterFlush() {
        UUID userId = UUID.randomUUID();

        assertThatIllegalStateException().isThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.recordUserCreated(userId);
            service.recordUserUpdated(userId, Set.of("preferences"));
            service.recordStatusChanged(userId, "SUSPENDED");
            repository.flush();
            assertThat(repository.findAll()).filteredOn(entry -> userId.equals(entry.getUserId())).hasSize(3);
            throw new IllegalStateException("Falla del caso de uso");
        })).withMessage("Falla del caso de uso");

        transaction.executeWithoutResult(status ->
                assertThat(repository.findAll()).noneMatch(entry -> userId.equals(entry.getUserId())));
    }
}
