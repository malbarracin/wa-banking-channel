package com.bank.users.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bank.users.user.dto.AuditEventType;
import com.bank.users.user.entity.AuditEvent;
import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.repository.AuditEventRepository;
import com.bank.users.user.repository.UserRepository;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:userupdates;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("TASK-7: actualización de datos permitidos con persistencia y auditoría reales")
class UserUpdateIntegrationTest {

    private static final String URL = "/api/v1/users/{id}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository repository;

    @Autowired
    private AuditEventRepository auditRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        auditRepository.deleteAll();
        repository.deleteAll();
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"ACTIVE", "SUSPENDED"})
    @DisplayName("Actualiza displayName y email, renueva updatedAt y audita solo nombres de campos")
    void updatesBothFields(UserStatus userStatus) throws Exception {
        User user = persist("both-" + userStatus, userStatus);
        Map<String, Object> before = row(user.getId());

        mockMvc.perform(patch(URL, user.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Nuevo\",\"email\":\"nuevo@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Nuevo"))
                .andExpect(jsonPath("$.email").value("nuevo@example.com"))
                .andExpect(jsonPath("$.status").value(userStatus.name()))
                .andExpect(jsonPath("$.firstName").value("Juan"));

        Map<String, Object> after = row(user.getId());
        assertThat(after.get("DISPLAY_NAME")).isEqualTo("Nuevo");
        assertThat(after.get("EMAIL")).isEqualTo("nuevo@example.com");
        for (String column : List.of("DOCUMENT_TYPE", "DOCUMENT_NUMBER", "FIRST_NAME", "LAST_NAME",
                "BIRTH_DATE", "STATUS", "CREATED_AT")) {
            assertThat(after.get(column)).as(column).isEqualTo(before.get(column));
        }
        assertThat(after.get("UPDATED_AT")).isNotEqualTo(before.get("UPDATED_AT"));

        List<AuditEvent> events = auditRepository.findByUserIdOrderByOccurredAtDesc(user.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo(AuditEventType.MODIFICACION_DATOS);
        assertThat(events.get(0).getModifiedFields()).isEqualTo("displayName,email");
        assertThat(events.get(0).toString()).doesNotContain("Nuevo", "nuevo@example.com");
    }

    @Test
    @DisplayName("Actualización parcial conserva el campo omitido")
    void partialUpdateKeepsOmittedField() throws Exception {
        User user = persist("partial", UserStatus.ACTIVE);

        mockMvc.perform(patch(URL, user.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"otro@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Juancho"))
                .andExpect(jsonPath("$.email").value("otro@example.com"));

        assertThat(auditRepository.findByUserIdOrderByOccurredAtDesc(user.getId()))
                .singleElement().extracting(AuditEvent::getModifiedFields).isEqualTo("email");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"email\":\"no-es-email\"}", "{\"displayName\":null}"})
    @DisplayName("Payload vacío o email inválido → 400 VALIDATION_ERROR sin cambios")
    void invalidPayload(String body) throws Exception {
        User user = persist("invalid-" + body.hashCode(), UserStatus.ACTIVE);
        Map<String, Object> before = row(user.getId());

        mockMvc.perform(patch(URL, user.getId()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(row(user.getId())).isEqualTo(before);
        assertThat(auditRepository.findByUserIdOrderByOccurredAtDesc(user.getId())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"displayName\":\"X\",\"firstName\":\"Otro\"}",
        "{\"email\":\"a@b.com\",\"preferences\":{\"lang\":\"es\"}}",
        "{\"status\":\"INACTIVE\"}",
        "{\"email\":\"no-es-email\",\"documentNumber\":\"1\"}"
    })
    @DisplayName("Campo no permitido → 400 BAD_REQUEST «campo no modificable» sin cambios parciales")
    void forbiddenField(String body) throws Exception {
        User user = persist("forbidden-" + body.hashCode(), UserStatus.ACTIVE);
        Map<String, Object> before = row(user.getId());

        mockMvc.perform(patch(URL, user.getId()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("campo no modificable por este servicio"));

        assertThat(row(user.getId())).isEqualTo(before);
        assertThat(auditRepository.findByUserIdOrderByOccurredAtDesc(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("Usuario inexistente → 404 NOT_FOUND")
    void unknownUser() throws Exception {
        mockMvc.perform(patch(URL, 999_999L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"X\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Valores iguales a los actuales → 200 sin modificación ni auditoría")
    void noEffectiveChange() throws Exception {
        User user = persist("same", UserStatus.ACTIVE);
        Map<String, Object> before = row(user.getId());

        mockMvc.perform(patch(URL, user.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Juancho\"}"))
                .andExpect(status().isOk());

        assertThat(row(user.getId())).isEqualTo(before);
        assertThat(auditRepository.findByUserIdOrderByOccurredAtDesc(user.getId())).isEmpty();
    }

    private Map<String, Object> row(Long id) {
        return jdbc.queryForMap("select * from users where id = ?", id);
    }

    private User persist(String documentNumber, UserStatus userStatus) {
        User user = new User();
        user.setDocumentType(DocumentType.DNI);
        user.setDocumentNumber(documentNumber);
        user.setFirstName("Juan");
        user.setLastName("Perez");
        user.setDisplayName("Juancho");
        user.setEmail("juan@example.com");
        user.setBirthDate(LocalDate.of(1990, 5, 20));
        user.setStatus(userStatus);
        return repository.saveAndFlush(user);
    }
}
