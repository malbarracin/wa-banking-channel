package com.bank.users.audit.repository;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bank.users.audit.entity.AuditAction;
import com.bank.users.audit.entity.AuditLog;
import com.bank.users.audit.service.AuditService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

@DataJpaTest
@Import(AuditService.class)
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuditLogRepositoryTest {

    private final AuditLogRepository repository;
    private final AuditService service;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;

    AuditLogRepositoryTest(AuditLogRepository repository, AuditService service, EntityManager entityManager,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.service = service;
        this.entityManager = entityManager;
        this.transactionManager = transactionManager;
    }

    @Test
    @DisplayName("Recupera las tres acciones y todos sus metadatos tras limpiar el contexto JPA")
    void retrievesPersistedAuditMetadata() {
        UUID userId = UUID.randomUUID();
        Instant before = Instant.now().minusSeconds(1);
        service.recordUserCreated(userId);
        service.recordUserUpdated(userId, Set.of("email"));
        service.recordStatusChanged(userId, "LOGICALLY_DELETED");

        repository.flush();
        entityManager.clear();

        assertThat(repository.findAll()).hasSize(3).allSatisfy(entry -> {
            assertThat(entry.getId()).isNotNull();
            assertThat(entry.getUserId()).isEqualTo(userId);
            assertThat(entry.getResult()).isEqualTo("SUCCESS");
            assertThat(entry.getOccurredAt()).isBetween(before, Instant.now().plusSeconds(1));
            assertThat(repository.findById(entry.getId())).isPresent();
        });
        assertThat(repository.findAll()).extracting(AuditLog::getAction)
                .containsExactlyInAnyOrder(AuditAction.values());
        assertThat(repository.findAll()).extracting(AuditLog::getDetail)
                .containsExactlyInAnyOrder("status=ACTIVE", "fields=email", "status=LOGICALLY_DELETED");
    }

    @Test
    @DisplayName("Una falla del caso de uso revierte incluso la auditoría enviada a la base")
    void rollsBackAuditWithCallingUseCase() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        UUID userId = UUID.randomUUID();

        assertThatIllegalStateException().isThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.recordUserCreated(userId);
            repository.flush();
            throw new IllegalStateException("Falla del caso de uso");
        }));

        entityManager.clear();
        assertThat(repository.findAll()).noneMatch(entry -> userId.equals(entry.getUserId()));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("No permite registrar auditoría fuera de una transacción del caso de uso")
    void requiresCallingTransaction() {
        assertThatExceptionOfType(IllegalTransactionStateException.class)
                .isThrownBy(() -> service.recordUserCreated(UUID.randomUUID()));
    }
}
