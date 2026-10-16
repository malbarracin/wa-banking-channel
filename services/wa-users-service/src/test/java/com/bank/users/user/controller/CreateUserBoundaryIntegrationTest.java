package com.bank.users.user.controller;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CreateUserBoundaryIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Autowired
    CreateUserBoundaryIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("camposInvalidos")
    @DisplayName("Cada campo inválido devuelve su detalle y audita un rechazo sin crear usuarios")
    void rechazaCampoInvalidoSinPersistirUsuario(String campo, String valor) throws Exception {
        ObjectNode solicitud = objectMapper.createObjectNode()
                .put("tipoDocumento", "DNI")
                .put("numeroDocumento", UUID.randomUUID().toString())
                .put("nombreTratamiento", "Cliente");
        solicitud.put(campo, valor);
        long usuariosAntes = jdbc.queryForObject("select count(*) from users", Long.class);
        long rechazosAntes = contarRechazos();

        String respuesta = mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(solicitud)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details[0].field").value(campo))
                .andExpect(jsonPath("$.details[0].message").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(Instant.parse(objectMapper.readTree(respuesta).get("timestamp").asText())).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from users", Long.class)).isEqualTo(usuariosAntes);
        assertThat(contarRechazos()).isEqualTo(rechazosAntes + 1);
    }

    @Test
    @DisplayName("El contacto opcional se conserva y la unicidad considera ambos componentes documentales")
    void conservaContactoYPermiteMismoNumeroConOtroTipo() throws Exception {
        String numeroDocumento = UUID.randomUUID().toString();
        crearConContacto("DNI", numeroDocumento);
        crearConContacto("PASAPORTE", numeroDocumento);

        assertThat(jdbc.queryForObject("select count(*) from users where numero_documento = ?",
                Long.class, numeroDocumento)).isEqualTo(2);
    }

    private void crearConContacto(String tipoDocumento, String numeroDocumento) throws Exception {
        String solicitud = objectMapper.writeValueAsString(Map.of(
                "tipoDocumento", tipoDocumento, "numeroDocumento", numeroDocumento,
                "nombreTratamiento", "María", "email", "cliente@example.com", "telefonoContacto", "+34123456789"));

        var respuesta = mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content(solicitud))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoDocumento").value(tipoDocumento))
                .andExpect(jsonPath("$.email").value("cliente@example.com"))
                .andExpect(jsonPath("$.telefonoContacto").value("+34123456789"))
                .andReturn().getResponse();

        JsonNode usuario = objectMapper.readTree(respuesta.getContentAsByteArray());
        UUID id = UUID.fromString(usuario.get("id").asText());
        assertThat(respuesta.getHeader("Location")).isEqualTo("/api/v1/users/" + id);
        assertThat(Instant.parse(usuario.get("updatedAt").asText()))
                .isEqualTo(Instant.parse(usuario.get("createdAt").asText()));
        assertThat(jdbc.queryForMap("select email, telefono_contacto from users where id = ?", id))
                .containsEntry("EMAIL", "cliente@example.com")
                .containsEntry("TELEFONO_CONTACTO", "+34123456789");
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_entries where user_id = ? and action = 'ALTA'
                and result = 'OK' and occurred_at is not null
                """, Long.class, id)).isEqualTo(1);
    }

    private long contarRechazos() {
        return jdbc.queryForObject("""
                select count(*) from audit_entries where user_id is null and action = 'ALTA'
                and result = 'RECHAZADO' and detail = 'DATOS_INVALIDOS' and occurred_at is not null
                """, Long.class);
    }

    private static Stream<Arguments> camposInvalidos() {
        return Stream.of(
                Arguments.of("tipoDocumento", (String) null),
                Arguments.of("tipoDocumento", ""),
                Arguments.of("tipoDocumento", " "),
                Arguments.of("tipoDocumento", "a".repeat(256)),
                Arguments.of("numeroDocumento", (String) null),
                Arguments.of("numeroDocumento", ""),
                Arguments.of("numeroDocumento", " "),
                Arguments.of("numeroDocumento", "a".repeat(256)),
                Arguments.of("nombreTratamiento", (String) null),
                Arguments.of("nombreTratamiento", ""),
                Arguments.of("nombreTratamiento", " "),
                Arguments.of("nombreTratamiento", "a".repeat(256)),
                Arguments.of("email", "correo-invalido"),
                Arguments.of("telefonoContacto", "1".repeat(256)));
    }
}
