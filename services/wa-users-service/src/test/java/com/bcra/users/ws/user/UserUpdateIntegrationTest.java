package com.bcra.users.ws.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import com.bcra.users.ws.audit.repository.AuditRepository;
import com.bcra.users.ws.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"bank.users.public-fields=status",
        "spring.datasource.url=jdbc:h2:mem:userupdate;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserUpdateIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private AuditRepository audits;

    @Test
    @DisplayName("Actualiza la fecha y filtra los datos de la respuesta según la política de privacidad")
    void actualizaFechaYFiltraRespuesta() throws Exception {
        UUID id = crearCliente();
        Instant fechaAnterior = users.findById(id).orElseThrow().getUpdatedAt();

        mvc.perform(patch("/api/v1/users/{id}", id).header("X-Actor", "operator")
                .contentType(MediaType.APPLICATION_JSON).content("{\"contactEmail\":\"nuevo@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.contactEmail").doesNotExist())
                .andExpect(jsonPath("$.uniquenessValue").doesNotExist());

        var cliente = users.findById(id).orElseThrow();
        assertThat(cliente.getContactEmail()).isEqualTo("nuevo@example.com");
        assertThat(cliente.getUpdatedAt()).isAfter(fechaAnterior);
        assertThat(audits.findByUserIdOrderByOccurredAtAsc(id)).singleElement().satisfies(entrada -> {
            assertThat(entrada.getChangedFields()).isEqualTo("contactEmail");
            assertThat(entrada.getActor()).isEqualTo("operator");
            assertThat(entrada.getOccurredAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("Rechaza cada formato de contacto inválido sin guardar cambios ni auditoría")
    void rechazaFormatosInvalidosSinCambios() throws Exception {
        UUID id = crearCliente();

        for (String campo : new String[] {"contactEmail", "contactPhone"}) {
            mvc.perform(patch("/api/v1/users/{id}", id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"displayName\":\"Otro\",\"" + campo + "\":\"invalid\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details[0].field").value(campo))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        assertThat(users.findById(id).orElseThrow().getDisplayName()).isEqualTo("Ana");
        assertThat(audits.findByUserIdOrderByOccurredAtAsc(id)).isEmpty();
    }

    @Test
    @DisplayName("Rechaza correo vacío sin persistir cambios ni registrar auditoría")
    void rechazaCorreoVacio() throws Exception {
        UUID id = crearCliente();

        mvc.perform(patch("/api/v1/users/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Otro\",\"contactEmail\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("contactEmail"));

        var cliente = users.findById(id).orElseThrow();
        assertThat(cliente.getDisplayName()).isEqualTo("Ana");
        assertThat(cliente.getContactEmail()).isNull();
        assertThat(audits.findByUserIdOrderByOccurredAtAsc(id)).isEmpty();
    }

    @Test
    @DisplayName("Audita solo los campos que cambian y omite auditoría cuando no hay cambios")
    void auditaSoloCamposModificados() throws Exception {
        UUID id = crearCliente();
        Instant fechaAnterior = users.findById(id).orElseThrow().getUpdatedAt();

        mvc.perform(patch("/api/v1/users/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Ana\"}"))
                .andExpect(status().isOk());
        assertThat(users.findById(id).orElseThrow().getUpdatedAt()).isEqualTo(fechaAnterior);
        assertThat(audits.findByUserIdOrderByOccurredAtAsc(id)).isEmpty();

        mvc.perform(patch("/api/v1/users/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Ana\",\"contactEmail\":\"nuevo@example.com\"}"))
                .andExpect(status().isOk());
        assertThat(audits.findByUserIdOrderByOccurredAtAsc(id)).singleElement()
                .satisfies(entrada -> assertThat(entrada.getChangedFields()).isEqualTo("contactEmail"));
    }

    private UUID crearCliente() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, external_id, uniqueness_field, display_name, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'Ana', 'ACTIVE', TIMESTAMP '2020-01-01 00:00:00', "
                + "TIMESTAMP '2020-01-01 00:00:00')", id, UUID.randomUUID().toString(),
                UUID.randomUUID().toString());
        return id;
    }
}
