package com.bcra.users.ws.user.service;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bcra.users.ws.user.entity.AuditAction;
import com.bcra.users.ws.user.entity.AuditLog;
import com.bcra.users.ws.user.entity.AuditResult;
import com.bcra.users.ws.user.repository.AuditLogRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@SpringBootTest
@ActiveProfiles("test")
class AuditServiceIntegrationTest {

    private final AuditService auditService;
    private final AuditLogRepository repository;
    private final PlatformTransactionManager transactionManager;

    @Autowired
    AuditServiceIntegrationTest(AuditService auditService, AuditLogRepository repository,
            PlatformTransactionManager transactionManager) {
        this.auditService = auditService;
        this.repository = repository;
        this.transactionManager = transactionManager;
    }

    @BeforeEach
    void limpiarAuditoria() {
        repository.deleteAll();
    }

    @ParameterizedTest
    @EnumSource(AuditAction.class)
    @DisplayName("Persiste cada acción exitosa con documento y fecha UTC")
    void persisteOperacionExitosa(AuditAction accion) {
        Instant inicio = Instant.now();

        AuditLog registro = auditService.registrar(accion, AuditResult.EXITOSO, "DNI", "12345678", "");
        AuditLog guardado = repository.findById(registro.getId()).orElseThrow();

        assertThat(guardado.getId()).isNotNull();
        assertThat(guardado.getFecha()).isBetween(inicio.minusSeconds(1), Instant.now());
        assertThat(guardado.getAccion()).isEqualTo(accion);
        assertThat(guardado.getResultado()).isEqualTo(AuditResult.EXITOSO);
        assertThat(guardado.getTipoDocumento()).isEqualTo("DNI");
        assertThat(guardado.getNumeroDocumento()).isEqualTo("12345678");
        assertThat(guardado.getMotivo()).isEmpty();
    }

    @Test
    @DisplayName("Conserva un rechazo y su motivo cuando se revierte la transacción principal")
    void conservaRechazoTrasRollback() {
        TransactionTemplate transaccion = new TransactionTemplate(transactionManager);

        UUID registroId = transaccion.execute(estado -> {
            AuditLog registro = auditService.registrar(AuditAction.ALTA, AuditResult.RECHAZADO,
                    "PASAPORTE", "ABC123", "Documento duplicado");
            estado.setRollbackOnly();
            return registro.getId();
        });

        AuditLog guardado = repository.findById(registroId).orElseThrow();
        assertThat(guardado.getResultado()).isEqualTo(AuditResult.RECHAZADO);
        assertThat(guardado.getMotivo()).isEqualTo("Documento duplicado");
        assertThat(guardado.getTipoDocumento()).isEqualTo("PASAPORTE");
        assertThat(guardado.getNumeroDocumento()).isEqualTo("ABC123");
        assertThat(guardado.getAccion()).isEqualTo(AuditAction.ALTA);
        assertThat(guardado.getFecha()).isNotNull();
    }

    @Test
    @DisplayName("Impide registrar un rechazo sin motivo")
    void rechazaResultadoSinMotivo() {
        assertThatIllegalArgumentException().isThrownBy(() -> auditService.registrar(
                AuditAction.CAMBIO_ESTADO, AuditResult.RECHAZADO, "DNI", "12345678", " "));

        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("Impide registrar una operación sin referencia al usuario")
    void rechazaDocumentoVacio() {
        assertThatIllegalArgumentException().isThrownBy(() -> auditService.registrar(
                AuditAction.ACTUALIZACION, AuditResult.EXITOSO, " ", "12345678", ""));

        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("Rechaza motivos que superan la capacidad de persistencia")
    void rechazaMotivoDemasiadoLargo() {
        String motivo = "a".repeat(256);

        assertThatIllegalArgumentException().isThrownBy(() -> auditService.registrar(
                AuditAction.ALTA, AuditResult.EXITOSO, "DNI", "12345678", motivo));

        assertThat(repository.count()).isZero();
    }
}
