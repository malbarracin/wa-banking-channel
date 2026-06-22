package com.wa.banking.users.api.v1.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wa.banking.users.api.v1.dto.CreateUserRequestV1;
import com.wa.banking.users.entity.AuditAction;
import com.wa.banking.users.entity.DocumentType;
import com.wa.banking.users.entity.UserStatus;
import com.wa.banking.users.repository.BankUserRepository;
import com.wa.banking.users.repository.UserAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración para flujos U1–U4 con Testcontainers Mongo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserControllerV1IntegrationTest {

    private static final DockerImageName MONGO_IMAGE = DockerImageName.parse("mongo:7");

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer(MONGO_IMAGE);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BankUserRepository bankUserRepository;

    @Autowired
    private UserAuditRepository userAuditRepository;

    @BeforeEach
    void setUp() {
        userAuditRepository.deleteAll();
        bankUserRepository.deleteAll();
    }

    @Test
    @DisplayName("U1 OK: POST usuario válido retorna 201 con ACTIVE y canLinkChannel=true")
    void shouldCreateUser_whenValidRequest() throws Exception {
        CreateUserRequestV1 request = buildCreateRequest("12345678");

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.canLinkChannel", is(true)))
                .andExpect(jsonPath("$.documentType", is("DNI")))
                .andExpect(jsonPath("$.documentNumber", is("12345678")))
                .andExpect(jsonPath("$.displayName", is("John Doe")))
                .andExpect(jsonPath("$.email", is("john@example.com")));
    }

    @Test
    @DisplayName("U1 Error: POST mismo documento dos veces retorna 400 BAD_REQUEST")
    void shouldReturnBadRequest_whenDuplicateDocument() throws Exception {
        CreateUserRequestV1 request = buildCreateRequest("87654321");
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")))
                .andExpect(jsonPath("$.message", is("User already exists with document DNI 87654321")))
                .andExpect(jsonPath("$.details", hasSize(0)))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("U2 OK: GET por id y por documento tras alta")
    void shouldFindUser_whenCreatedPreviously() throws Exception {
        CreateUserRequestV1 request = buildCreateRequest("11223344");
        String userId = createUserAndExtractId(request);

        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(userId)))
                .andExpect(jsonPath("$.documentNumber", is("11223344")));

        mockMvc.perform(get("/api/v1/users/by-document")
                        .param("documentType", "DNI")
                        .param("documentNumber", "11223344"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(userId)))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    @DisplayName("U2 Error: GET by-document inexistente retorna 404 NOT_FOUND")
    void shouldReturnNotFound_whenDocumentDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/users/by-document")
                        .param("documentType", "DNI")
                        .param("documentNumber", "99999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("User not found with document DNI 99999999")))
                .andExpect(jsonPath("$.details", hasSize(0)))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("U2 Error: GET id inexistente retorna 404 NOT_FOUND")
    void shouldReturnNotFound_whenUserIdDoesNotExist() throws Exception {
        String missingId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/v1/users/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("User not found with id " + missingId)))
                .andExpect(jsonPath("$.details", hasSize(0)))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("U3 OK: PATCH campos permitidos persiste cambios")
    void shouldUpdateUser_whenValidPatchRequest() throws Exception {
        String userId = createUserAndExtractId(buildCreateRequest("55667788"));

        String updateBody = """
                {
                  "displayName": "Jane Doe",
                  "email": "jane@example.com",
                  "phone": "+541198765432"
                }
                """;

        mockMvc.perform(patch("/api/v1/users/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName", is("Jane Doe")))
                .andExpect(jsonPath("$.email", is("jane@example.com")))
                .andExpect(jsonPath("$.phone", is("+541198765432")));

        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName", is("Jane Doe")))
                .andExpect(jsonPath("$.email", is("jane@example.com")));

        mockMvc.perform(get("/api/v1/users/{id}/audit", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].action", is(AuditAction.UPDATED.name())))
                .andExpect(jsonPath("$.content[0].changedFields",
                        containsInAnyOrder("displayName", "email", "phone")))
                .andExpect(jsonPath("$.content[0].result", is("SUCCESS")));
    }

    @Test
    @DisplayName("U4 OK: PATCH status a SUSPENDED crea audit entry y canLinkChannel=false")
    void shouldSuspendUser_whenValidStatusChange() throws Exception {
        String userId = createUserAndExtractId(buildCreateRequest("99887766"));

        mockMvc.perform(patch("/api/v1/users/{id}/status", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUSPENDED")))
                .andExpect(jsonPath("$.canLinkChannel", is(false)));

        mockMvc.perform(get("/api/v1/users/{id}/audit", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].action", is(AuditAction.STATUS_CHANGED.name())))
                .andExpect(jsonPath("$.content[0].previousStatus", is("ACTIVE")))
                .andExpect(jsonPath("$.content[0].newStatus", is("SUSPENDED")))
                .andExpect(jsonPath("$.content[0].result", is("SUCCESS")));
    }

    @Test
    @DisplayName("Error validación: POST body incompleto retorna 400 VALIDATION_ERROR con error contract")
    void shouldReturnValidationError_whenCreateRequestIncomplete() throws Exception {
        String incompleteBody = """
                {
                  "documentType": "DNI"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incompleteBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.message", is("Validation failed")))
                .andExpect(jsonPath("$.details", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("U4 Error: transición de estado inválida retorna 400 BAD_REQUEST")
    void shouldReturnBadRequest_whenInvalidStatusTransition() throws Exception {
        String userId = createUserAndExtractId(buildCreateRequest("44556677"));

        mockMvc.perform(patch("/api/v1/users/{id}/status", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SOFT_DELETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SOFT_DELETED")));

        mockMvc.perform(patch("/api/v1/users/{id}/status", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")))
                .andExpect(jsonPath("$.message",
                        is("Transition from SOFT_DELETED to ACTIVE is not allowed")));
    }

    private CreateUserRequestV1 buildCreateRequest(String documentNumber) {
        return new CreateUserRequestV1(
                DocumentType.DNI,
                documentNumber,
                "John Doe",
                "john@example.com",
                "+541112345678",
                Map.of("lang", "es")
        );
    }

    private String createUserAndExtractId(CreateUserRequestV1 request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String userId = response.get("id").asText();
        assertThat(userId).isNotBlank();
        return userId;
    }
}
