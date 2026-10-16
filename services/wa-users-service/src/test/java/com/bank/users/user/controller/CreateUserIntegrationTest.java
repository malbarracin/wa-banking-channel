package com.bank.users.user.controller;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CreateUserIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Autowired
    CreateUserIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("El alta mínima devuelve 201, persiste usuario activo y audita el éxito")
    void creaUsuarioActivoYAuditaExito() throws Exception {
        String document = UUID.randomUUID().toString();

        String body = mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content(request(document)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVO"))
                .andExpect(jsonPath("$.aptoParaVinculoCanal").value(true))
                .andExpect(jsonPath("$.numeroDocumento").value(document))
                .andExpect(jsonPath("$.nombreTratamiento").value("Cliente"))
                .andExpect(jsonPath("$.preferencias").isMap())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_entries where user_id = ? and action = 'ALTA'
                and result = 'OK' and occurred_at is not null
                """, Long.class, id)).isEqualTo(1);
    }

    @Test
    @DisplayName("El duplicado devuelve conflicto y deja un solo usuario y un rechazo auditado")
    void rechazaDuplicadoSinCrearUsuario() throws Exception {
        String document = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content(request(document))).andExpect(status().isCreated());
        long before = rejectionCount("USUARIO_DUPLICADO");

        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(request(document)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(jdbc.queryForObject("select count(*) from users where numero_documento = ?",
                Long.class, document)).isEqualTo(1);
        assertThat(rejectionCount("USUARIO_DUPLICADO")).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("Los datos mínimos ausentes o inválidos se auditan sin crear usuarios")
    void rechazaDatosInvalidosAntesDelService() throws Exception {
        long usersBefore = jdbc.queryForObject("select count(*) from users", Long.class);
        long before = rejectionCount("DATOS_INVALIDOS");

        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.length()").value(3))
                .andExpect(jsonPath("$.timestamp").exists());
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("""
                {"tipoDocumento":" ","numeroDocumento":"1","nombreTratamiento":"Cliente","email":"malo"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(jdbc.queryForObject("select count(*) from users", Long.class)).isEqualTo(usersBefore);
        assertThat(rejectionCount("DATOS_INVALIDOS")).isEqualTo(before + 2);
    }

    @Test
    @DisplayName("El JSON ilegible se audita y conserva el contrato de error uniforme")
    void auditaJsonIlegible() throws Exception {
        long before = rejectionCount("DATOS_INVALIDOS");

        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(rejectionCount("DATOS_INVALIDOS")).isEqualTo(before + 1);
    }

    private long rejectionCount(String detail) {
        return jdbc.queryForObject("""
                select count(*) from audit_entries where user_id is null and action = 'ALTA'
                and result = 'RECHAZADO' and detail = ? and occurred_at is not null
                """, Long.class, detail);
    }

    private String request(String document) {
        return """
                {"tipoDocumento":"DNI","numeroDocumento":"%s","nombreTratamiento":"Cliente"}
                """.formatted(document);
    }
}
