package com.bank.users.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.params.provider.CsvSource;
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
import com.bank.users.user.dto.AuditResult;
import com.bank.users.user.entity.AuditEvent;
import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.repository.AuditEventRepository;
import com.bank.users.user.repository.UserRepository;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:userstatus;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("TASK-8: suspensión, reactivación y baja lógica auditadas")
class UserStatusChangeIntegrationTest {

    private static final String URL = "/api/v1/users/{id}/status";
    private static final String ELIGIBLE_URL = "/api/v1/users/{id}/eligible";

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

    @ParameterizedTest(name = "{0} -> {1} (eligible={2})")
    @CsvSource({
        "ACTIVE, SUSPENDED, false",
        "ACTIVE, INACTIVE, false",
        "SUSPENDED, ACTIVE, true",
        "SUSPENDED, INACTIVE, false"
    })
    @DisplayName("Transición válida: 200, renueva updatedAt, audita CAMBIO_ESTADO y ajusta elegibilidad")
    void validTransition(UserStatus from, UserStatus to, boolean eligible) throws Exception {
        User user = persist("ok-" + from + "-" + to, from);
        Map<String, Object> before = row(user.getId());

        mockMvc.perform(patch(URL, user.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + to + "\",\"reason\":\"Solicitud del cliente\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.status").value(to.name()))
                .andExpect(jsonPath("$.documentNumber").value("ok-" + from + "-" + to));

        Map<String, Object> after = row(user.getId());
        assertThat(after.get("STATUS")).isEqualTo(to.name());
        assertThat(after.get("DOCUMENT_NUMBER")).isEqualTo(before.get("DOCUMENT_NUMBER"));
        assertThat(after.get("CREATED_AT")).isEqualTo(before.get("CREATED_AT"));
        assertThat(after.get("UPDATED_AT")).isNotEqualTo(before.get("UPDATED_AT"));

        List<AuditEvent> events = auditRepository.findByUserIdOrderByOccurredAtDesc(user.getId());
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo(AuditEventType.CAMBIO_ESTADO);
            assertThat(event.getPreviousStatus()).isEqualTo(from);
            assertThat(event.getNewStatus()).isEqualTo(to);
            assertThat(event.getResult()).isEqualTo(AuditResult.OK);
            assertThat(event.getOccurredAt()).isNotNull();
        });

        mockMvc.perform(get(ELIGIBLE_URL, user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(eligible));
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("Desde INACTIVE: 400 'la baja lógica es un estado terminal' y el registro se conserva")
    void inactiveIsTerminal(UserStatus target) throws Exception {
        User user = persist("term-" + target, UserStatus.INACTIVE);
        Map<String, Object> before = row(user.getId());

        mockMvc.perform(patch(URL, user.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + target + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("la baja lógica es un estado terminal"));

        assertThat(row(user.getId())).isEqualTo(before);
        assertThat(auditRepository.findByUserIdOrderByOccurredAtDesc(user.getId())).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"ACTIVE", "SUSPENDED"})
    @DisplayName("Mismo estado: 400 BAD_REQUEST sin modificar ni auditar")
    void sameStatusRejected(UserStatus current) throws Exception {
        User user = persist("same-" + current, current);
        Map<String, Object> before = row(user.getId());

        mockMvc.perform(patch(URL, user.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + current + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        assertThat(row(user.getId())).isEqualTo(before);
        assertThat(auditRepository.findByUserIdOrderByOccurredAtDesc(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("status ausente: 400 VALIDATION_ERROR sin cambios")
    void missingStatus() throws Exception {
        User user = persist("missing", UserStatus.ACTIVE);

        mockMvc.perform(patch(URL, user.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("status"));

        assertThat(row(user.getId()).get("STATUS")).isEqualTo("ACTIVE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"BLOCKED", "active", "DELETED"})
    @DisplayName("status desconocido: 400 BAD_REQUEST sin cambios")
    void unknownStatus(String value) throws Exception {
        User user = persist("unknown-" + value, UserStatus.ACTIVE);

        mockMvc.perform(patch(URL, user.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + value + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        assertThat(row(user.getId()).get("STATUS")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("id inexistente: 404 NOT_FOUND")
    void unknownUser() throws Exception {
        mockMvc.perform(patch(URL, 999_999L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Suspensión, reactivación y baja: historial completo y documento conservado")
    void fullLifecycleKeepsHistory() throws Exception {
        User user = persist("lifecycle", UserStatus.ACTIVE);
        for (UserStatus target : List.of(UserStatus.SUSPENDED, UserStatus.ACTIVE, UserStatus.INACTIVE)) {
            mockMvc.perform(patch(URL, user.getId()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"" + target + "\"}"))
                    .andExpect(status().isOk());
        }

        assertThat(repository.findByDocumentTypeAndDocumentNumber(DocumentType.DNI, "lifecycle"))
                .get().extracting(User::getStatus).isEqualTo(UserStatus.INACTIVE);
        assertThat(auditRepository.findByUserIdOrderByOccurredAtDesc(user.getId()))
                .hasSize(3)
                .allMatch(event -> event.getEventType() == AuditEventType.CAMBIO_ESTADO);
        mockMvc.perform(get("/api/v1/users/{id}", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
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
        user.setBirthDate(LocalDate.of(1990, 5, 20));
        user.setStatus(userStatus);
        return repository.saveAndFlush(user);
    }
}
