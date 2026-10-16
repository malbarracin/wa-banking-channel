package com.bank.users.user.service;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.AopTestUtils;

import com.bank.users.audit.entity.AuditAction;
import com.bank.users.audit.service.AuditService;
import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.user.exception.DuplicateUserException;
import com.bank.users.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@MockitoSpyBean(types = {UserRepository.class, AuditService.class})
class UserServiceIntegrationTest {

    private final UserService service;
    private final UserRepository repository;
    private final AuditService auditService;
    private final JdbcTemplate jdbc;
    private final MockMvc mockMvc;

    @Autowired
    UserServiceIntegrationTest(UserService service, UserRepository repository, AuditService auditService,
            JdbcTemplate jdbc, MockMvc mockMvc) {
        this.service = service;
        this.repository = repository;
        this.auditService = auditService;
        this.jdbc = jdbc;
        this.mockMvc = mockMvc;
    }

    @Test
    @DisplayName("La constraint real rechaza un duplicado aunque la comprobación previa no lo detecte")
    void traduceColisionDeConstraintTrasRollback() {
        String document = UUID.randomUUID().toString();
        CreateUserRequest request = new CreateUserRequest("DNI", document, "Cliente", "cliente@example.com", "123");
        service.create(request);
        doReturn(false).when(repository).existsByTipoDocumentoAndNumeroDocumento("DNI", document);
        long before = rejectionCount();

        assertThatExceptionOfType(DuplicateUserException.class).isThrownBy(() -> service.create(request))
                .withCauseInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("select count(*) from users where numero_documento = ?",
                Long.class, document)).isEqualTo(1);
        assertThat(rejectionCount()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("Si la auditoría OK falla se revierte también el usuario")
    void revierteUsuarioSiFallaAuditoria() {
        String document = UUID.randomUUID().toString();
        long before = jdbc.queryForObject("select count(*) from audit_entries where result = 'OK'", Long.class);
        AuditService auditTarget = AopTestUtils.getUltimateTargetObject(auditService);
        doThrow(new IllegalStateException("Falla simulada"))
                .when(auditTarget).recordSuccess(any(UUID.class), any(AuditAction.class));

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> service.create(
                new CreateUserRequest("DNI", document, "Cliente", null, null)));

        assertThat(jdbc.queryForObject("select count(*) from users where numero_documento = ?",
                Long.class, document)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_entries where result = 'OK'", Long.class))
                .isEqualTo(before);
    }

    @Test
    @DisplayName("Una falla de persistencia ajena a unicidad devuelve 500 seguro y se audita")
    void conservaErrorInternoSinConfundirloConDuplicado() throws Exception {
        String document = UUID.randomUUID().toString();
        doThrow(new DataIntegrityViolationException("Detalle interno SQL"))
                .when(repository).saveAndFlush(any());
        long before = rejectionCount();

        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("""
                {"tipoDocumento":"DNI","numeroDocumento":"%s","nombreTratamiento":"Cliente"}
                """.formatted(document)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Error interno"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(rejectionCount()).isEqualTo(before + 1);
        assertThat(jdbc.queryForObject("select count(*) from users where numero_documento = ?",
                Long.class, document)).isZero();
    }

    private long rejectionCount() {
        return jdbc.queryForObject("""
                select count(*) from audit_entries where user_id is null and action = 'ALTA'
                and result = 'RECHAZADO' and occurred_at is not null
                """, Long.class);
    }
}
