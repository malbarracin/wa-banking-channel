package com.bank.users.user.controller;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.bank.users.audit.entity.AuditAction;
import com.bank.users.audit.service.AuditService;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FindUserIntegrationTest {

    private final MockMvc mockMvc;
    private final UserRepository repository;
    private final AuditService auditService;
    private final TransactionTemplate transaction;
    private final ObjectMapper objectMapper;

    @Autowired
    FindUserIntegrationTest(MockMvc mockMvc, UserRepository repository, AuditService auditService,
            org.springframework.transaction.PlatformTransactionManager transactionManager, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.repository = repository;
        this.auditService = auditService;
        this.transaction = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("La consulta del piloto devuelve datos autorizados y aptitud para cada estado")
    void devuelveDatosAutorizadosYEstado(UserStatus userStatus) throws Exception {
        User user = createUser(userStatus);

        String body = mockMvc.perform(get("/api/v1/users/{id}", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.tipoDocumento").value("DNI"))
                .andExpect(jsonPath("$.numeroDocumento").value(user.getNumeroDocumento()))
                .andExpect(jsonPath("$.nombreTratamiento").value("Cliente"))
                .andExpect(jsonPath("$.email").value("cliente@example.com"))
                .andExpect(jsonPath("$.telefonoContacto").value("123456789"))
                .andExpect(jsonPath("$.preferencias.idioma").value("es"))
                .andExpect(jsonPath("$.status").value(userStatus.name()))
                .andExpect(jsonPath("$.aptoParaVinculoCanal").value(userStatus == UserStatus.ACTIVO))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.historialResumido").isEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body).size()).isEqualTo(12);
    }

    @Test
    @DisplayName("El historial limita a 20 eventos propios recientes y solo expone campos de auditoría seguros")
    void devuelveHistorialAcotadoSinDatosPersonales() throws Exception {
        User user = createUser(UserStatus.ACTIVO);
        User otherUser = createUser(UserStatus.ACTIVO);
        transaction.executeWithoutResult(ignored -> {
            auditService.recordSuccess(otherUser.getId(), AuditAction.ALTA);
            for (int index = 0; index < 21; index++) {
                auditService.recordSuccess(user.getId(), AuditAction.ACTUALIZACION);
            }
        });

        String body = mockMvc.perform(get("/api/v1/users/{id}", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historialResumido.length()").value(20))
                .andReturn().getResponse().getContentAsString();

        JsonNode history = objectMapper.readTree(body).get("historialResumido");
        for (JsonNode entry : history) {
            assertThat(entry.size()).isEqualTo(4);
            assertThat(entry.get("action").asText()).isEqualTo("ACTUALIZACION");
            assertThat(entry.get("result").asText()).isEqualTo("OK");
            assertThat(entry.get("detail").asText()).isEqualTo("OPERACION_CONFIRMADA");
            assertThat(entry.get("occurredAt").asText()).isNotBlank();
        }
        assertThat(auditService.findHistory(user.getId())).hasSize(20);
    }

    @Test
    @DisplayName("Un UUID inexistente devuelve 404 con el contrato uniforme")
    void devuelveNotFoundParaUsuarioInexistente() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Un UUID mal formado devuelve 400 con el contrato uniforme")
    void devuelveBadRequestParaUuidInvalido() throws Exception {
        mockMvc.perform(get("/api/v1/users/no-es-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private User createUser(UserStatus userStatus) {
        User user = new User();
        user.setTipoDocumento("DNI");
        user.setNumeroDocumento(UUID.randomUUID().toString());
        user.setNombreTratamiento("Cliente");
        user.setEmail("cliente@example.com");
        user.setTelefonoContacto("123456789");
        user.setPreferencias(Map.of("idioma", "es"));
        user.setStatus(userStatus);
        return repository.saveAndFlush(user);
    }
}
