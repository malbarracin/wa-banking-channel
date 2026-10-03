package com.bank.users.user.service;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.bank.users.user.dto.AuditEventType;
import com.bank.users.user.dto.AuditRejectionReason;
import com.bank.users.user.dto.AuditResult;
import com.bank.users.user.entity.AuditEvent;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.repository.AuditEventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@DisplayName("AuditEventService — auditoría durable del ciclo de vida")
class AuditEventServiceTest {

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
    @DisplayName("Persiste ALTA, CAMBIO_ESTADO y MODIFICACION_DATOS con usuario, fecha y resultado OK")
    void recordsSuccessfulEvents() {
        // When
        AuditEvent alta = service.recordUserCreated(USER_ID);
        AuditEvent cambio = service.recordStatusChanged(USER_ID, UserStatus.ACTIVE, UserStatus.SUSPENDED);
        AuditEvent modificacion = service.recordDataModified(USER_ID, List.of("email", "displayName", "email"));

        // Then
        assertThat(List.of(alta, cambio, modificacion)).allSatisfy(event -> {
            assertThat(event.getId()).isNotNull();
            assertThat(event.getUserId()).isEqualTo(USER_ID);
            assertThat(event.getOccurredAt()).isNotNull();
            assertThat(event.getResult()).isEqualTo(AuditResult.OK);
            assertThat(event.getReason()).isNull();
        });
        assertThat(alta.getEventType()).isEqualTo(AuditEventType.ALTA);
        assertThat(cambio.getEventType()).isEqualTo(AuditEventType.CAMBIO_ESTADO);
        assertThat(cambio.getPreviousStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(cambio.getNewStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(modificacion.getEventType()).isEqualTo(AuditEventType.MODIFICACION_DATOS);
        assertThat(modificacion.getModifiedFields()).isEqualTo("displayName,email");
    }

    @Test
    @DisplayName("ALTA_RECHAZADA persiste motivo y fecha aunque la transacción del alta revierta")
    void rejectedCreationSurvivesRollback() {
        // Given / When: el alta falla dentro de su transacción después de auditar el rechazo
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            service.recordCreationRejected(AuditRejectionReason.DUPLICATE_DOCUMENT);
            service.recordUserCreated(USER_ID);
            throw new IllegalStateException("alta rechazada");
        })).isInstanceOf(IllegalStateException.class);

        // Then: solo sobrevive el rechazo; el ALTA de la transacción revertida no
        List<AuditEvent> events = repository.findAll();
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo(AuditEventType.ALTA_RECHAZADA);
            assertThat(event.getResult()).isEqualTo(AuditResult.REJECTED);
            assertThat(event.getReason()).isEqualTo(AuditRejectionReason.DUPLICATE_DOCUMENT);
            assertThat(event.getOccurredAt()).isNotNull();
            assertThat(event.getUserId()).isNull();
        });
    }

    @Test
    @DisplayName("Recupera los eventos del usuario por occurredAt descendente, sin eventos ajenos")
    void findsUserEventsNewestFirst() {
        // Given
        Instant base = Instant.parse("2026-10-03T10:00:00Z");
        repository.save(event(USER_ID, AuditEventType.ALTA, base));
        repository.save(event(USER_ID, AuditEventType.MODIFICACION_DATOS, base.plusSeconds(120)));
        repository.save(event(USER_ID, AuditEventType.CAMBIO_ESTADO, base.plusSeconds(60)));
        repository.save(event(7L, AuditEventType.ALTA, base.plusSeconds(300)));

        // When
        List<AuditEvent> events = service.findByUser(USER_ID);

        // Then
        assertThat(events).extracting(AuditEvent::getEventType).containsExactly(
                AuditEventType.MODIFICACION_DATOS, AuditEventType.CAMBIO_ESTADO, AuditEventType.ALTA);
        assertThat(service.findByUser(999L)).isEmpty();
    }

    @Test
    @DisplayName("Rechaza registrar valores en vez de nombres de campo, o sin campos")
    void rejectsFieldValues() {
        assertThatThrownBy(() -> service.recordDataModified(USER_ID, List.of("juan@example.com")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordDataModified(USER_ID, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordDataModified(USER_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordUserCreated(null))
                .isInstanceOf(NullPointerException.class);
        assertThat(repository.count()).isZero();
    }

    private static AuditEvent event(Long userId, AuditEventType type, Instant occurredAt) {
        return AuditEvent.builder()
                .userId(userId)
                .eventType(type)
                .result(AuditResult.OK)
                .occurredAt(occurredAt)
                .build();
    }
}
