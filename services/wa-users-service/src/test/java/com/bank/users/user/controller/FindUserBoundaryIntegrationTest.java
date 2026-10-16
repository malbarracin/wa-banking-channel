package com.bank.users.user.controller;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.bank.users.user.entity.User;
import com.bank.users.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FindUserBoundaryIntegrationTest {

    private static final int HISTORY_LIMIT = 20;
    private static final Instant FIRST_EVENT_TIME = Instant.parse("2026-01-01T00:00:00Z");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    FindUserBoundaryIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, UserRepository repository,
            JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("La consulta recupera un alta real con contacto opcional vacío y no modifica usuario ni auditoría")
    void consultaAltaRealSinModificarDatos() throws Exception {
        String request = """
                {"tipoDocumento":"DNI","numeroDocumento":"%s","nombreTratamiento":"Cliente"}
                """.formatted(UUID.randomUUID());
        String createdBody = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createdBody);
        UUID userId = UUID.fromString(created.get("id").asText());

        String body = mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historialResumido.length()").value(1))
                .andExpect(jsonPath("$.historialResumido[0].action").value("ALTA"))
                .andExpect(jsonPath("$.historialResumido[0].result").value("OK"))
                .andReturn().getResponse().getContentAsString();

        JsonNode found = objectMapper.readTree(body);
        assertThat(found.get("email").isNull()).isTrue();
        assertThat(found.get("telefonoContacto").isNull()).isTrue();
        assertThat(found.get("preferencias").isObject()).isTrue();
        assertThat(found.get("preferencias").isEmpty()).isTrue();
        assertThat(found.get("createdAt")).isEqualTo(created.get("createdAt"));
        assertThat(found.get("updatedAt")).isEqualTo(created.get("updatedAt"));
        assertThat(repository.findById(userId)).hasValueSatisfying(user ->
                assertThat(user.getUpdatedAt()).isEqualTo(Instant.parse(created.get("updatedAt").asText())));
        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_entries where user_id = ?",
                Long.class, userId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("El historial devuelve los 20 eventos más nuevos en orden descendente e incluye rechazos")
    void ordenaYRecortaHistorialConResultadosMixtos() throws Exception {
        User user = createUser();
        int eventCount = HISTORY_LIMIT + 2;
        for (int index = 0; index < eventCount; index++) {
            insertEvent(user.getId(), FIRST_EVENT_TIME.plusSeconds(index));
        }
        insertEvent(UUID.randomUUID(), FIRST_EVENT_TIME.plusSeconds(eventCount));
        insertEvent(null, FIRST_EVENT_TIME.plusSeconds(eventCount));

        String body = mockMvc.perform(get("/api/v1/users/{id}", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historialResumido.length()").value(HISTORY_LIMIT))
                .andReturn().getResponse().getContentAsString();

        JsonNode history = objectMapper.readTree(body).get("historialResumido");
        for (int index = 0; index < HISTORY_LIMIT; index++) {
            JsonNode entry = history.get(index);
            assertThat(Instant.parse(entry.get("occurredAt").asText()))
                    .isEqualTo(FIRST_EVENT_TIME.plusSeconds(eventCount - index - 1));
            assertThat(entry.get("action").asText()).isEqualTo("CAMBIO_ESTADO");
            assertThat(entry.get("result").asText()).isEqualTo("RECHAZADO");
            assertThat(entry.get("detail").asText()).isEqualTo("OPERACION_FALLIDA");
            assertThat(entry.size()).isEqualTo(4);
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_entries where user_id = ?",
                Long.class, user.getId())).isEqualTo((long) eventCount);
    }

    private User createUser() {
        User user = new User();
        user.setTipoDocumento("DNI");
        user.setNumeroDocumento(UUID.randomUUID().toString());
        user.setNombreTratamiento("Cliente");
        return repository.saveAndFlush(user);
    }

    private void insertEvent(UUID userId, Instant occurredAt) {
        jdbcTemplate.update("""
                insert into audit_entries (id, user_id, action, result, detail, occurred_at)
                values (?, ?, 'CAMBIO_ESTADO', 'RECHAZADO', 'OPERACION_FALLIDA', ?)
                """, UUID.randomUUID(), userId, Timestamp.from(occurredAt));
    }
}
