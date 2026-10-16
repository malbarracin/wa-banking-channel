package com.bank.users.user.controller;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.bank.users.audit.entity.AuditAction;
import com.bank.users.audit.entity.AuditDetail;
import com.bank.users.audit.entity.AuditResult;
import com.bank.users.audit.service.AuditService;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChangeUserStatusIntegrationTest {

    private final MockMvc mockMvc;
    private final UserRepository repository;
    private final AuditService auditService;

    @Autowired
    ChangeUserStatusIntegrationTest(MockMvc mockMvc, UserRepository repository, AuditService auditService) {
        this.mockMvc = mockMvc;
        this.repository = repository;
        this.auditService = auditService;
    }

    @ParameterizedTest
    @CsvSource({"ACTIVO,SUSPENDIDO", "SUSPENDIDO,ACTIVO", "ACTIVO,BAJA_LOGICA", "SUSPENDIDO,BAJA_LOGICA"})
    @DisplayName("La matriz permite cuatro transiciones y conserva el registro físico con auditoría OK")
    void persisteTransicionesPermitidas(UserStatus initial, UserStatus target) throws Exception {
        User user = createUser(initial);

        mockMvc.perform(patch("/api/v1/users/{id}/status", user.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + target + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.status").value(target.name()))
                .andExpect(jsonPath("$.aptoParaVinculoCanal").value(target == UserStatus.ACTIVO));

        User saved = repository.findById(user.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(target);
        assertThat(saved.getNumeroDocumento()).isEqualTo(user.getNumeroDocumento());
        assertThat(saved.getCreatedAt()).isEqualTo(user.getCreatedAt());
        assertAudit(user.getId(), AuditResult.OK, AuditDetail.OPERACION_CONFIRMADA);
    }

    @ParameterizedTest
    @CsvSource({"ACTIVO,ACTIVO", "SUSPENDIDO,SUSPENDIDO", "BAJA_LOGICA,ACTIVO",
            "BAJA_LOGICA,SUSPENDIDO", "BAJA_LOGICA,BAJA_LOGICA"})
    @DisplayName("Las otras cinco transiciones devuelven 409 y el rechazo sobrevive al rollback")
    void rechazaFueraDeMatriz(UserStatus initial, UserStatus target) throws Exception {
        User user = createUser(initial);

        mockMvc.perform(patch("/api/v1/users/{id}/status", user.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + target + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isString());

        assertThat(repository.findById(user.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(user);
        assertAudit(user.getId(), AuditResult.RECHAZADO, AuditDetail.TRANSICION_NO_PERMITIDA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":\"OTRO\"}",
            "{\"status\":0}", "{\"status\":true}", "null", "{", ""})
    @DisplayName("Un estado ausente o inválido devuelve 400, conserva los datos y audita el rechazo")
    void rechazaDatosInvalidos(String body) throws Exception {
        User user = createUser(UserStatus.ACTIVO);

        mockMvc.perform(patch("/api/v1/users/{id}/status", user.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.isOneOf("BAD_REQUEST", "VALIDATION_ERROR")))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isString());

        assertThat(repository.findById(user.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(user);
        assertAudit(user.getId(), AuditResult.RECHAZADO, AuditDetail.DATOS_INVALIDOS);
    }

    @Test
    @DisplayName("El usuario inexistente devuelve 404 auditado y un UUID inválido devuelve 400")
    void rechazaIdentificadoresInvalidos() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(patch("/api/v1/users/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDIDO\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isString());
        assertAudit(id, AuditResult.RECHAZADO, AuditDetail.USUARIO_NO_ENCONTRADO);
        mockMvc.perform(patch("/api/v1/users/invalido/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDIDO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    private void assertAudit(UUID id, AuditResult expected, AuditDetail detail) {
        assertThat(auditService.findHistory(id)).singleElement().satisfies(entry -> {
            assertThat(entry.getAction()).isEqualTo(AuditAction.CAMBIO_ESTADO);
            assertThat(entry.getResult()).isEqualTo(expected);
            assertThat(entry.getDetail()).isEqualTo(detail.name());
            assertThat(entry.getOccurredAt()).isNotNull();
        });
    }

    private User createUser(UserStatus initial) {
        User user = new User();
        user.setTipoDocumento("DNI");
        user.setNumeroDocumento(UUID.randomUUID().toString());
        user.setNombreTratamiento("Cliente");
        user.setStatus(initial);
        return repository.saveAndFlush(user);
    }
}
