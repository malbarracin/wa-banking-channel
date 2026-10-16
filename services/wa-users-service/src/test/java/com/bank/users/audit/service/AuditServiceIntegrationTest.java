package com.bank.users.audit.service;

import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bank.users.audit.entity.AuditAction;
import com.bank.users.audit.entity.AuditDetail;
import com.bank.users.audit.entity.AuditEntry;
import com.bank.users.audit.entity.AuditResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@SpringBootTest
class AuditServiceIntegrationTest {

    private final AuditService service;
    private final TransactionTemplate transaction;
    private final EntityManager entityManager;

    @Autowired
    AuditServiceIntegrationTest(AuditService service, PlatformTransactionManager transactionManager,
            EntityManager entityManager) {
        this.service = service;
        this.transaction = new TransactionTemplate(transactionManager);
        this.entityManager = entityManager;
    }

    @Test
    @DisplayName("Persiste y lee acciones confirmadas con identificador, fecha y motivo seguro")
    void persisteOperacionesConfirmadas() {
        UUID userId = UUID.randomUUID();

        transaction.executeWithoutResult(status -> {
            for (AuditAction action : AuditAction.values()) {
                service.recordSuccess(userId, action);
            }
        });

        assertThat(service.findHistory(userId)).hasSize(3).allSatisfy(entry -> {
            assertThat(entry.getId()).isNotNull();
            assertThat(entry.getUserId()).isEqualTo(userId);
            assertThat(entry.getOccurredAt()).isNotNull();
            assertThat(entry.getResult()).isEqualTo(AuditResult.OK);
            assertThat(entry.getDetail()).isEqualTo(AuditDetail.OPERACION_CONFIRMADA.name());
        });
        assertThat(service.findHistory(userId)).extracting(AuditEntry::getAction)
                .containsExactlyInAnyOrder(AuditAction.values());
    }

    @Test
    @DisplayName("El rechazo persiste aunque la operación llamadora arroje una excepción y revierta")
    void conservaRechazoTrasRollback() {
        UUID userId = UUID.randomUUID();

        assertThatIllegalArgumentException().isThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.recordRejection(userId, AuditAction.CAMBIO_ESTADO, AuditDetail.TRANSICION_NO_PERMITIDA);
            throw new IllegalArgumentException("Operación rechazada");
        }));

        assertThat(service.findHistory(userId)).singleElement().satisfies(entry -> {
            assertThat(entry.getResult()).isEqualTo(AuditResult.RECHAZADO);
            assertThat(entry.getDetail()).isEqualTo(AuditDetail.TRANSICION_NO_PERMITIDA.name());
        });
    }

    @Test
    @DisplayName("Un OK enviado a la base desaparece si el caso de uso revierte")
    void revierteExitoNoConfirmado() {
        UUID userId = UUID.randomUUID();

        transaction.executeWithoutResult(status -> {
            service.recordSuccess(userId, AuditAction.ACTUALIZACION);
            entityManager.flush();
            service.recordRejection(userId, AuditAction.ACTUALIZACION, AuditDetail.CAMPOS_NO_PERMITIDOS);
            status.setRollbackOnly();
        });

        assertThat(service.findHistory(userId)).singleElement()
                .extracting(AuditEntry::getResult).isEqualTo(AuditResult.RECHAZADO);
    }

    @Test
    @DisplayName("No permite registrar un OK sin la transacción del usuario")
    void exigeTransaccionParaExito() {
        UUID userId = UUID.randomUUID();

        assertThatExceptionOfType(IllegalTransactionStateException.class)
                .isThrownBy(() -> service.recordSuccess(userId, AuditAction.ALTA));

        assertThat(service.findHistory(userId)).isEmpty();
    }

    @Test
    @DisplayName("Registra alta rechazada sin usuario y sin vínculo de clave foránea")
    void admiteAltaRechazadaSinUsuario() {
        service.recordRejection(null, AuditAction.ALTA, AuditDetail.USUARIO_DUPLICADO);

        transaction.executeWithoutResult(status -> {
            var entries = entityManager.createQuery("""
                    select entry from AuditEntry entry where entry.userId is null
                    and entry.detail = :detail
                    """, AuditEntry.class)
                    .setParameter("detail", AuditDetail.USUARIO_DUPLICADO.name()).getResultList();
            assertThat(entries).isNotEmpty().allSatisfy(entry -> {
                assertThat(entry.getUserId()).isNull();
                assertThat(entry.getAction()).isEqualTo(AuditAction.ALTA);
                assertThat(entry.getResult()).isEqualTo(AuditResult.RECHAZADO);
            });
        });
    }

    @Test
    @DisplayName("El historial es inmutable, limitado a veinte y separado por usuario")
    void limitaHistorialRecientePorUsuario() {
        UUID userId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();
        transaction.executeWithoutResult(status -> {
            for (int index = 0; index < 22; index++) {
                service.recordSuccess(userId, AuditAction.ACTUALIZACION);
            }
            service.recordSuccess(anotherUserId, AuditAction.ALTA);
        });

        var history = service.findHistory(userId);

        assertThat(history).hasSize(20).allSatisfy(entry -> assertThat(entry.getUserId()).isEqualTo(userId));
        assertThat(history).extracting(AuditEntry::getOccurredAt).isSortedAccordingTo((first, second) ->
                second.compareTo(first));
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(history::clear);
        assertThat(service.findHistory(anotherUserId)).hasSize(1);
        assertThat(service.findHistory(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("Rechaza motivos inválidos y campos obligatorios ausentes sin insertar registros")
    void rechazaArgumentosInvalidos() {
        UUID userId = UUID.randomUUID();

        assertThatIllegalArgumentException().isThrownBy(() ->
                service.recordRejection(userId, AuditAction.ALTA, AuditDetail.OPERACION_CONFIRMADA));
        assertThatIllegalArgumentException().isThrownBy(() ->
                service.recordRejection(userId, AuditAction.ALTA, null));
        assertThatNullPointerException().isThrownBy(() ->
                service.recordRejection(userId, null, AuditDetail.DATOS_INVALIDOS));
        assertThatNullPointerException().isThrownBy(() -> service.findHistory(null));
        assertThatNullPointerException().isThrownBy(() -> transaction.executeWithoutResult(status ->
                service.recordSuccess(null, AuditAction.ALTA)));

        assertThat(service.findHistory(userId)).isEmpty();
    }
}
