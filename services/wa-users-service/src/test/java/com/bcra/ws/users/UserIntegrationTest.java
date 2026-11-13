package com.bcra.ws.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetSocketAddress;
import java.net.Socket;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bcra.ws.users.domain.AuditAction;
import com.bcra.ws.users.domain.AuditResult;
import com.bcra.ws.users.entity.UserAuditEventEntity;
import com.bcra.ws.users.entity.UserEntity;
import com.bcra.ws.users.repository.UserAuditEventRepository;
import com.bcra.ws.users.repository.UserRepository;

/**
 * End-to-end integration tests for U1–U4 against real MongoDB 7.
 * <p>
 * Persistence strategy (equivalent to Testcontainers when Docker Desktop npipe
 * is incompatible with the Testcontainers client): Mongo via {@code docker compose up -d mongo}
 * on {@code localhost:27017}, database {@code wa_users_it}.
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserIntegrationTest {

    private static final String MONGO_HOST = "localhost";
    private static final int MONGO_PORT = 27017;
    private static final String MONGO_DB = "wa_users_it";

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri",
                () -> "mongodb://" + MONGO_HOST + ":" + MONGO_PORT + "/" + MONGO_DB);
        registry.add("spring.profiles.active", () -> "local");
    }

    @BeforeAll
    static void requireMongoAvailable() {
        assumeTrue(isMongoReachable(),
                () -> "MongoDB not reachable at " + MONGO_HOST + ":" + MONGO_PORT
                        + ". Start with: docker compose up -d mongo");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuditEventRepository auditEventRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private static String createdUserId;

    private static boolean cleaned;

    @Test
    @Order(100)
    @DisplayName("Mongo mantiene índices independientes y PATCH propio conserva datos y audita")
    void shouldEnforceIndependentIndexesAndSafePatch() throws Exception {
        UserEntity first = userRepository.findById(createdUserId).orElseThrow();
        UserEntity other = first.toBuilder().id(null).documentNumber("99887766")
                .email("independent@example.com").build();
        other = userRepository.save(other);
        final UserEntity second = other;
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> userRepository.save(first.toBuilder()
                .id(null).documentNumber("88776655").build()))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class)
                .hasMessageContaining("uk_email");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> userRepository.save(first.toBuilder()
                .id(null).email("different@example.com").build()))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class)
                .hasMessageContaining("uk_document");
        mockMvc.perform(patch("/api/v1/users/{id}", second.getId()).contentType(MediaType.APPLICATION_JSON)
                        .header("X-Actor-Id", "editor")
                        .content("{\"email\":\"" + first.getEmail() + "\",\"address\":\"Rejected\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[0].field").value("email"));
        assertThat(userRepository.findById(second.getId()).orElseThrow().getAddress())
                .isEqualTo(second.getAddress());
        mockMvc.perform(patch("/api/v1/users/{id}", second.getId()).contentType(MediaType.APPLICATION_JSON)
                        .header("X-Actor-Id", "editor")
                        .content("{\"email\":\"independent@example.com\",\"address\":\"New address\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("New address"))
                .andExpect(jsonPath("$.firstName").value(second.getFirstName()));
        assertThat(auditEventRepository.findByUserIdOrderByTimestampDesc(second.getId()))
                .anySatisfy(event -> {
                    assertThat(event.getAction()).isEqualTo(AuditAction.USER_UPDATED);
                    assertThat(event.getResult()).isEqualTo(AuditResult.FAILURE);
                    assertThat(event.getActor()).isEqualTo("editor");
                    assertThat(event.getTimestamp()).isNotNull();
                });
    }

    @BeforeEach
    void cleanOnce() {
        if (!cleaned) {
            mongoTemplate.dropCollection(UserEntity.class);
            mongoTemplate.dropCollection(UserAuditEventEntity.class);
            new com.bcra.ws.users.repository.UserIndexInitializer(mongoTemplate).run(null);
            cleaned = true;
        }
    }

    @Test
    @Order(1)
    @DisplayName("U1 — Alta OK → status ACTIVE + auditoría USER_CREATED")
    void shouldCreateUserActive_whenValidRequest() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Actor-Id", "tester")
                        .content("""
                                {
                                  "documentType": "DNI",
                                  "documentNumber": "40111222",
                                  "firstName": "Ana",
                                  "lastName": "Perez",
                                  "email": "ana.perez@example.com",
                                  "phone": "+54911111111",
                                  "preferredName": "Anita",
                                  "address": "Av. Corrientes 1234",
                                  "birthDate": "1990-05-20"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.channelLinkEligible").value(true))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.documentNumber").value("40111222"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        createdUserId = body.get("id").asText();
        assertThat(createdUserId).isNotBlank();

        UserEntity persisted = userRepository.findById(createdUserId).orElseThrow();
        assertThat(persisted.getAddress()).isEqualTo("Av. Corrientes 1234");
        assertThat(persisted.getBirthDate()).isEqualTo(java.time.LocalDate.of(1990, 5, 20));
        assertThat(persisted.getPreferredLanguage()).isEqualTo("es");
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
        assertThat(body.get("address").asText()).isEqualTo(persisted.getAddress());
        assertThat(body.get("birthDate").asText()).isEqualTo("1990-05-20");
        assertThat(body.get("preferredLanguage").asText()).isEqualTo("es");

        assertThat(auditEventRepository.findByUserIdOrderByTimestampDesc(createdUserId))
                .anySatisfy(event -> {
                    assertThat(event.getAction()).isEqualTo(AuditAction.USER_CREATED);
                    assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
                    assertThat(event.getActor()).isEqualTo("tester");
                });
    }

    @Test
    @Order(2)
    @DisplayName("U1 — Alta duplicada → 409 CONFLICT")
    void shouldRejectDuplicate_whenSameDocument() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentType": "DNI",
                                  "documentNumber": "40111222",
                                  "firstName": "Ana",
                                  "lastName": "Perez",
                                  "email": "ana.perez@example.com",
                                  "phone": "+54911111111",
                                  "address": "Av. Corrientes 1234",
                                  "birthDate": "1990-05-20"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details[0].field").value("documentNumber"));
    }

    @Test
    @Order(3)
    @DisplayName("U2 — Consulta por id OK")
    void shouldFindById_whenExists() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", createdUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdUserId))
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.address").value("Av. Corrientes 1234"))
                .andExpect(jsonPath("$.birthDate").value("1990-05-20"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @Order(4)
    @DisplayName("U2 — Consulta por documento OK")
    void shouldFindByDocument_whenExists() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .param("documentType", "DNI")
                        .param("documentNumber", "40111222"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdUserId));
    }

    @Test
    @Order(5)
    @DisplayName("U2 — Consulta 404 cuando no existe")
    void shouldReturnNotFound_whenIdMissing() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", "000000000000000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @Order(6)
    @DisplayName("U3 — Update campos permitidos OK + auditoría")
    void shouldUpdateAllowedFields_whenValid() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}", createdUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Actor-Id", "editor")
                        .content("""
                                {
                                  "preferredName": "Ani",
                                  "email": "ani.perez@example.com",
                                  "phone": "+54922222222",
                                  "preferredLanguage": "en"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredName").value("Ani"))
                .andExpect(jsonPath("$.email").value("ani.perez@example.com"))
                .andExpect(jsonPath("$.preferredLanguage").value("en"));

        assertThat(auditEventRepository.findByUserIdOrderByTimestampDesc(createdUserId))
                .anySatisfy(event -> {
                    assertThat(event.getAction()).isEqualTo(AuditAction.USER_UPDATED);
                    assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
                });
    }

    @Test
    @Order(7)
    @DisplayName("U3 — Update sin campos → 400 BAD_REQUEST")
    void shouldRejectUpdate_whenNoFields() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}", createdUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @Order(8)
    @DisplayName("U4 — Cambio estado ACTIVE→SUSPENDED + channelLinkEligible false + auditoría")
    void shouldSuspendUser_whenTransitionAllowed() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/status", createdUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Actor-Id", "ops")
                        .content("""
                                {
                                  "status": "SUSPENDED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.channelLinkEligible").value(false));

        assertThat(auditEventRepository.findByUserIdOrderByTimestampDesc(createdUserId))
                .anySatisfy(event -> {
                    assertThat(event.getAction()).isEqualTo(AuditAction.STATUS_CHANGED);
                    assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
                    assertThat(event.getSummary()).containsEntry("from", "ACTIVE");
                    assertThat(event.getSummary()).containsEntry("to", "SUSPENDED");
                });
    }

    @Test
    @Order(9)
    @DisplayName("U4 — Transición inválida SUSPENDED→SUSPENDED → 409")
    void shouldRejectInvalidTransition_whenSameStatus() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/status", createdUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "SUSPENDED"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @Order(10)
    @DisplayName("U4 — Baja lógica terminal; rechazo auditado y usuario conservado")
    void shouldRejectReactivation_withAuditTrail() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/status", createdUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "LOGICALLY_DELETED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGICALLY_DELETED"))
                .andExpect(jsonPath("$.channelLinkEligible").value(false));

        var priorEvents = auditEventRepository.findByUserIdOrderByTimestampDesc(createdUserId);
        UserEntity beforeReactivation = userRepository.findById(createdUserId).orElseThrow();
        mockMvc.perform(patch("/api/v1/users/{id}/status", createdUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details[0].field").value("status"));

        mockMvc.perform(get("/api/v1/users/{id}/audit", createdUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].userId").value(createdUserId));

        UserEntity afterReactivation = userRepository.findById(createdUserId).orElseThrow();
        assertThat(afterReactivation).usingRecursiveComparison().isEqualTo(beforeReactivation);
        var events = auditEventRepository.findByUserIdOrderByTimestampDesc(createdUserId);
        assertThat(events).hasSize(priorEvents.size() + 1);
        assertThat(events).extracting(UserAuditEventEntity::getId)
                .containsAll(priorEvents.stream().map(UserAuditEventEntity::getId).toList());
        assertThat(events).extracting(UserAuditEventEntity::getTimestamp).isSortedAccordingTo(
                java.util.Comparator.reverseOrder());
        assertThat(events.get(0).getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(events.get(0).getActor()).isEqualTo("SYSTEM");
        assertThat(events.get(0).getSummary())
                .containsEntry("from", "LOGICALLY_DELETED").containsEntry("to", "ACTIVE");
    }

    private static boolean isMongoReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(MONGO_HOST, MONGO_PORT), 2000);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
