package com.bank.users.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:usercreation;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("TASK-6: alta de clientes sin duplicar documentos, con auditoría durable")
class UserCreationIntegrationTest {

    private static final String USERS = "/api/v1/users";
    private static final String REJECTED_COUNT =
            "select count(*) from audit_events where event_type = 'ALTA_RECHAZADA'";
    private static final String LAST_REJECTED = "select user_id, result, reason, occurred_at from audit_events "
            + "where event_type = 'ALTA_RECHAZADA' order by id desc limit 1";
    private static final int CONCURRENT_REQUESTS = 8;
    private static final long TIMEOUT_SECONDS = 30;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Alta válida devuelve 201 ACTIVE, audita ALTA y se recupera por id y documento")
    void validCreationReturnsCreatedAndAudits() throws Exception {
        Instant before = Instant.now();
        String body = mockMvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON)
                        .content(payload("ok-1").toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.documentType").value("DNI"))
                .andExpect(jsonPath("$.documentNumber").value("ok-1"))
                .andExpect(jsonPath("$.displayName").value("Juancho"))
                .andExpect(jsonPath("$.email").value("juan@example.com"))
                .andExpect(jsonPath("$.birthDate").value("1990-05-20"))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(body);
        long id = created.get("id").asLong();
        Instant createdAt = Instant.parse(created.get("createdAt").asText());
        assertThat(createdAt).isAfterOrEqualTo(before.minusSeconds(1));
        assertThat(Instant.parse(created.get("updatedAt").asText())).isEqualTo(createdAt);

        mockMvc.perform(get(USERS + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(get(USERS).param("documentType", "DNI").param("documentNumber", "ok-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));

        List<Map<String, Object>> audit = jdbc.queryForList(
                "select event_type, result, occurred_at from audit_events where user_id = ?", id);
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0)).containsEntry("EVENT_TYPE", "ALTA").containsEntry("RESULT", "OK");
        assertThat(audit.get(0).get("OCCURRED_AT")).isNotNull();
    }

    @Test
    @DisplayName("displayName y email son opcionales")
    void optionalFieldsMayBeOmitted() throws Exception {
        ObjectNode payload = payload("ok-optional");
        payload.remove("displayName");
        payload.remove("email");

        mockMvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON).content(payload.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"documentType", "documentNumber", "firstName", "lastName", "birthDate"})
    @DisplayName("Un obligatorio ausente devuelve 400 VALIDATION_ERROR sin crear registro")
    void missingRequiredFieldIsRejected(String field) throws Exception {
        ObjectNode payload = payload("missing-" + field);
        payload.remove(field);
        assertRejectedWithoutWrites(payload, "VALIDATION_ERROR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"documentNumber", "firstName", "lastName"})
    @DisplayName("Un obligatorio en blanco devuelve 400 VALIDATION_ERROR sin crear registro")
    void blankRequiredFieldIsRejected(String field) throws Exception {
        ObjectNode payload = payload("blank-" + field);
        payload.put(field, "   ");
        assertRejectedWithoutWrites(payload, "VALIDATION_ERROR");
    }

    @Test
    @DisplayName("Email con formato inválido devuelve 400 VALIDATION_ERROR sin crear registro")
    void invalidEmailIsRejected() throws Exception {
        assertRejectedWithoutWrites(payload("bad-email").put("email", "no-es-email"), "VALIDATION_ERROR");
    }

    @Test
    @DisplayName("Tipo de documento o fecha con valor inválido devuelve 400 BAD_REQUEST sin crear registro")
    void invalidValuesAreRejected() throws Exception {
        assertRejectedWithoutWrites(payload("bad-type").put("documentType", "XYZ"), "BAD_REQUEST");
        assertRejectedWithoutWrites(payload("bad-date").put("birthDate", "20-05-1990"), "BAD_REQUEST");
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("Documento existente en cualquier estado devuelve 409 y audita ALTA_RECHAZADA")
    void duplicateInEveryStateIsRejected(UserStatus existingStatus) throws Exception {
        String number = "dup-" + existingStatus;
        persist(number, existingStatus);
        long users = repository.count();
        long rejectedBefore = rejectedCount();

        mockMvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON).content(payload(number).toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        assertThat(repository.count()).isEqualTo(users);
        assertThat(rejectedCount()).isEqualTo(rejectedBefore + 1);
        Map<String, Object> event = jdbc.queryForMap(LAST_REJECTED);
        assertThat(event).containsEntry("RESULT", "REJECTED").containsEntry("REASON", "DUPLICATE_DOCUMENT");
        assertThat(event.get("USER_ID")).isNull();
        assertThat(event.get("OCCURRED_AT")).isNotNull();
    }

    @Test
    @DisplayName("Altas concurrentes del mismo documento crean exactamente un usuario; el resto recibe 409")
    void concurrentCreationsProduceSingleUser() throws Exception {
        String number = "concurrent-1";
        long rejectedBefore = rejectedCount();
        String body = payload(number).toString();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                Callable<Integer> call = () -> {
                    start.await();
                    return mockMvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON).content(body))
                            .andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }

            assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
            assertThat(statuses).filteredOn(s -> s == 409).hasSize(CONCURRENT_REQUESTS - 1);
            assertThat(jdbc.queryForObject("select count(*) from users where document_number = ?",
                    Long.class, number)).isEqualTo(1L);
            assertThat(rejectedCount()).isEqualTo(rejectedBefore + CONCURRENT_REQUESTS - 1);
        } finally {
            pool.shutdownNow();
        }
    }

    private void assertRejectedWithoutWrites(ObjectNode payload, String code) throws Exception {
        long users = repository.count();
        long events = jdbc.queryForObject("select count(*) from audit_events", Long.class);

        mockMvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON).content(payload.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));

        assertThat(repository.count()).isEqualTo(users);
        assertThat(jdbc.queryForObject("select count(*) from audit_events", Long.class)).isEqualTo(events);
    }

    private long rejectedCount() {
        return jdbc.queryForObject(REJECTED_COUNT, Long.class);
    }

    private ObjectNode payload(String documentNumber) {
        return objectMapper.createObjectNode()
                .put("documentType", "DNI")
                .put("documentNumber", documentNumber)
                .put("firstName", "Juan")
                .put("lastName", "Perez")
                .put("displayName", "Juancho")
                .put("email", "juan@example.com")
                .put("birthDate", "1990-05-20");
    }

    private void persist(String documentNumber, UserStatus userStatus) {
        User user = new User();
        user.setDocumentType(DocumentType.DNI);
        user.setDocumentNumber(documentNumber);
        user.setFirstName("Ana");
        user.setLastName("Gomez");
        user.setBirthDate(LocalDate.of(1985, 1, 1));
        user.setStatus(userStatus);
        repository.saveAndFlush(user);
    }
}
