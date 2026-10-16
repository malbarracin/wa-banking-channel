package com.bank.users.audit.repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.bank.users.audit.entity.AuditAction;
import com.bank.users.audit.entity.AuditDetail;
import com.bank.users.audit.entity.AuditEntry;
import com.bank.users.audit.entity.AuditResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest
@Import(AuditEntryRepository.class)
class AuditEntryRepositoryTest {

    private final AuditEntryRepository repository;
    private final EntityManager entityManager;

    @Autowired
    AuditEntryRepositoryTest(AuditEntryRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Test
    @DisplayName("Recupera todos los campos desde la base después de limpiar el contexto JPA")
    void recuperaRegistroPersistidoSinCacheDePrimerNivel() {
        UUID userId = UUID.randomUUID();
        Instant beforeCreation = Instant.now();
        AuditEntry entry = new AuditEntry(userId, AuditAction.CAMBIO_ESTADO,
                AuditResult.RECHAZADO, AuditDetail.TRANSICION_NO_PERMITIDA);
        repository.append(entry);
        entityManager.flush();
        entityManager.clear();

        var history = repository.findRecentByUserId(userId, 1);

        assertThat(history).singleElement().satisfies(persisted -> {
            assertThat(persisted).isNotSameAs(entry);
            assertThat(persisted.getId()).isEqualTo(entry.getId());
            assertThat(persisted.getUserId()).isEqualTo(userId);
            assertThat(persisted.getAction()).isEqualTo(AuditAction.CAMBIO_ESTADO);
            assertThat(persisted.getResult()).isEqualTo(AuditResult.RECHAZADO);
            assertThat(persisted.getDetail()).isEqualTo(AuditDetail.TRANSICION_NO_PERMITIDA.name());
            assertThat(persisted.getOccurredAt().truncatedTo(ChronoUnit.MILLIS))
                    .isBetween(beforeCreation.truncatedTo(ChronoUnit.MILLIS), Instant.now());
        });
    }

    @Test
    @DisplayName("Conserva eventos repetidos como registros distintos sin sobrescribir el historial")
    void agregaEventosRepetidosSinSobrescribir() {
        UUID userId = UUID.randomUUID();
        AuditEntry first = new AuditEntry(userId, AuditAction.ALTA,
                AuditResult.RECHAZADO, AuditDetail.USUARIO_DUPLICADO);
        AuditEntry second = new AuditEntry(userId, AuditAction.ALTA,
                AuditResult.RECHAZADO, AuditDetail.USUARIO_DUPLICADO);

        repository.append(first);
        repository.append(second);
        entityManager.flush();
        entityManager.clear();

        assertThat(first.getId()).isNotEqualTo(second.getId());
        var history = repository.findRecentByUserId(userId, 2);
        assertThat(history).extracting(AuditEntry::getId).containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(history::clear);
    }
}
