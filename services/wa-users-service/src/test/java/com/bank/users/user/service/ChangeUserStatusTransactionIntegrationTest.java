package com.bank.users.user.service;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bank.users.audit.entity.AuditAction;
import com.bank.users.audit.entity.AuditDetail;
import com.bank.users.audit.entity.AuditResult;
import com.bank.users.audit.service.AuditService;
import com.bank.users.user.dto.ChangeUserStatusRequest;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.exception.InvalidUserStatusTransitionException;
import com.bank.users.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@SpringBootTest
class ChangeUserStatusTransactionIntegrationTest {

    private final UserService service;
    private final UserRepository repository;
    private final AuditService auditService;
    private final TransactionTemplate transaction;

    @Autowired
    ChangeUserStatusTransactionIntegrationTest(UserService service, UserRepository repository,
            AuditService auditService, PlatformTransactionManager transactionManager) {
        this.service = service;
        this.repository = repository;
        this.auditService = auditService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Test
    @DisplayName("El rollback del caso de uso revierte juntos el estado y la auditoría OK")
    void revierteEstadoYAuditoriaCuandoNoSeConfirmaLaTransaccion() {
        User user = crearUsuario(UserStatus.ACTIVO);

        transaction.executeWithoutResult(status -> {
            service.changeStatus(user.getId(), new ChangeUserStatusRequest(UserStatus.BAJA_LOGICA));
            assertThat(repository.findById(user.getId()).orElseThrow().getStatus())
                    .isEqualTo(UserStatus.BAJA_LOGICA);
            assertThat(auditService.findHistory(user.getId())).singleElement().satisfies(entry -> {
                assertThat(entry.getAction()).isEqualTo(AuditAction.CAMBIO_ESTADO);
                assertThat(entry.getResult()).isEqualTo(AuditResult.OK);
            });
            status.setRollbackOnly();
        });

        assertThat(repository.findById(user.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(user);
        assertThat(auditService.findHistory(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("Un rechazo de cambio de estado sobrevive al rollback de la transacción exterior")
    void conservaRechazoDelServicioTrasRollbackExterior() {
        User user = crearUsuario(UserStatus.BAJA_LOGICA);

        assertThatExceptionOfType(InvalidUserStatusTransitionException.class)
                .isThrownBy(() -> transaction.executeWithoutResult(status ->
                        service.changeStatus(user.getId(), new ChangeUserStatusRequest(UserStatus.ACTIVO))));

        assertThat(repository.findById(user.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(user);
        assertThat(auditService.findHistory(user.getId())).singleElement().satisfies(entry -> {
            assertThat(entry.getUserId()).isEqualTo(user.getId());
            assertThat(entry.getAction()).isEqualTo(AuditAction.CAMBIO_ESTADO);
            assertThat(entry.getResult()).isEqualTo(AuditResult.RECHAZADO);
            assertThat(entry.getDetail()).isEqualTo(AuditDetail.TRANSICION_NO_PERMITIDA.name());
            assertThat(entry.getOccurredAt()).isNotNull();
        });
    }

    private User crearUsuario(UserStatus status) {
        User user = new User();
        user.setTipoDocumento("DNI");
        user.setNumeroDocumento(UUID.randomUUID().toString());
        user.setNombreTratamiento("Cliente");
        user.setStatus(status);
        return repository.saveAndFlush(user);
    }
}
