package com.bank.users.user.repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import com.bank.users.user.dto.AuditEventType;
import com.bank.users.user.dto.AuditRejectionReason;
import com.bank.users.user.dto.AuditResult;
import com.bank.users.user.entity.AuditEvent;
import com.bank.users.user.entity.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("AuditEventRepository — recuperación de metadatos persistidos")
class AuditEventRepositoryTest {

    private static final Long USER_ID = 42L;
    private static final Instant BASE_TIME = Instant.parse("2026-10-03T10:00:00Z");

    @Autowired
    private AuditEventRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @ParameterizedTest
    @EnumSource(AuditEventType.class)
    @DisplayName("Recarga cada tipo de evento con fecha, resultado y sus metadatos sin crear usuarios")
    void reloadsEventMetadata(AuditEventType type) {
        // Given
        boolean rejected = type == AuditEventType.ALTA_RECHAZADA;
        AuditEvent.AuditEventBuilder builder = AuditEvent.builder()
                .eventType(type)
                .userId(rejected ? null : USER_ID)
                .result(rejected ? AuditResult.REJECTED : AuditResult.OK);
        if (type == AuditEventType.CAMBIO_ESTADO) {
            builder.previousStatus(UserStatus.SUSPENDED).newStatus(UserStatus.INACTIVE);
        } else if (type == AuditEventType.MODIFICACION_DATOS) {
            builder.modifiedFields("displayName,email");
        } else if (rejected) {
            builder.reason(AuditRejectionReason.DUPLICATE_DOCUMENT);
        }
        AuditEvent expected = builder.build();
        Instant beforeSave = Instant.now();

        // When: vaciar el contexto fuerza una lectura real de la base de datos.
        Long id = repository.saveAndFlush(expected).getId();
        Instant afterSave = Instant.now();
        entityManager.clear();
        AuditEvent actual = repository.findById(id).orElseThrow();

        // Then
        assertThat(actual).isNotSameAs(expected);
        assertThat(actual.getId()).isNotNull();
        assertThat(actual.getOccurredAt())
                .isBetween(beforeSave.minus(1, ChronoUnit.MICROS), afterSave.plus(1, ChronoUnit.MICROS))
                .isCloseTo(expected.getOccurredAt(), within(1, ChronoUnit.MICROS));
        assertThat(actual).usingRecursiveComparison().ignoringFields("occurredAt").isEqualTo(expected);
    }

    @Test
    @DisplayName("Ordena fechas explícitas descendentes y excluye otros usuarios y rechazos sin usuario")
    void queriesOnlyRequestedUserNewestFirst() {
        // Given: insertar fuera del orden cronológico.
        repository.saveAndFlush(createdEvent(USER_ID, BASE_TIME.plusSeconds(60)));
        repository.saveAndFlush(createdEvent(7L, BASE_TIME.plusSeconds(180)));
        repository.saveAndFlush(createdEvent(USER_ID, BASE_TIME));
        repository.saveAndFlush(createdEvent(USER_ID, BASE_TIME.plusSeconds(120)));
        repository.saveAndFlush(AuditEvent.builder()
                .eventType(AuditEventType.ALTA_RECHAZADA)
                .result(AuditResult.REJECTED)
                .reason(AuditRejectionReason.DUPLICATE_DOCUMENT)
                .occurredAt(BASE_TIME.plusSeconds(240))
                .build());
        entityManager.clear();

        // When / Then
        assertThat(repository.findByUserIdOrderByOccurredAtDesc(USER_ID))
                .extracting(AuditEvent::getOccurredAt)
                .containsExactly(BASE_TIME.plusSeconds(120), BASE_TIME.plusSeconds(60), BASE_TIME);
        assertThat(repository.findByUserIdOrderByOccurredAtDesc(999L)).isEmpty();
    }

    private static AuditEvent createdEvent(Long userId, Instant occurredAt) {
        return AuditEvent.builder()
                .userId(userId)
                .eventType(AuditEventType.ALTA)
                .result(AuditResult.OK)
                .occurredAt(occurredAt)
                .build();
    }
}
