package com.bank.users.user.controller;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.bank.users.audit.entity.AuditAction;
import com.bank.users.audit.repository.AuditLogRepository;
import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CreateUserIntegrationTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final AuditLogRepository auditRepository;

    CreateUserIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, UserRepository userRepository,
            AuditLogRepository auditRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
    }

    @BeforeEach
    void clearRows() {
        auditRepository.deleteAll();
        userRepository.deleteAll();
    }

    @ParameterizedTest
    @CsvSource(value = {"ana@example.com,NULL", "NULL,+5491112345678", "ana@example.com,+5491112345678"},
            nullValues = "NULL")
    @DisplayName("El alta admite email, teléfono o ambos y confirma su auditoría")
    void createsWithEachContactCombination(String email, String phone) throws Exception {
        CreateUserRequest request = new CreateUserRequest("DNI", "12345678", "Ana", email, phone,
                LocalDate.of(1990, 1, 2));

        String body = mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.channelLinkEligible").value(true))
                .andExpect(jsonPath("$.documentType").value("DNI"))
                .andExpect(jsonPath("$.documentNumber").value("12345678"))
                .andExpect(jsonPath("$.displayName").value("Ana"))
                .andReturn().getResponse().getContentAsString();

        JsonNode response = objectMapper.readTree(body);
        Set<String> fields = new HashSet<>();
        response.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "documentType", "documentNumber", "displayName",
                "email", "phone", "birthDate", "status", "preferences", "channelLinkEligible");
        UUID id = UUID.fromString(response.get("id").asText());
        User stored = userRepository.findById(id).orElseThrow();
        assertThat(stored.getEmail()).isEqualTo(email);
        assertThat(stored.getPhone()).isEqualTo(phone);
        assertThat(stored.getPreferences()).isEmpty();
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
        assertThat(auditRepository.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.getUserId()).isEqualTo(id);
            assertThat(entry.getAction()).isEqualTo(AuditAction.USER_CREATED);
            assertThat(entry.getResult()).isEqualTo("SUCCESS");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"DNI", "PASSPORT", "LC", "LE", "CUIT"})
    @DisplayName("Se acepta cada tipo del catálogo inicial")
    void acceptsDocumentCatalog(String documentType) throws Exception {
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(documentType, "AB1234",
                                "Ana", "ana@example.com", null, LocalDate.of(1990, 1, 2)))))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"documentType\":\"OTHER\"}",
        "{\"documentType\":\"DNI\",\"documentNumber\":\"123\",\"displayName\":\"Ana\","
                + "\"birthDate\":\"1990-01-02\"}",
        "{\"documentType\":\"DNI\",\"documentNumber\":\"123\",\"displayName\":\"Ana\","
                + "\"email\":\"invalid\",\"birthDate\":\"1990-01-02\"}",
        "{\"documentType\":\"DNI\",\"documentNumber\":\"123\",\"displayName\":\"Ana\","
                + "\"phone\":\"invalid\",\"birthDate\":\"1990-01-02\"}",
        "{\"documentType\":\"DNI\",\"documentNumber\":\"123\",\"displayName\":\"Ana\","
                + "\"email\":\"ana@example.com\",\"birthDate\":\"2990-01-02\"}"
    })
    @DisplayName("Datos incompletos o inválidos devuelven el contrato de validación sin persistir")
    void rejectsInvalidInput(String body) throws Exception {
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(userRepository.count()).isZero();
        assertThat(auditRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "SUSPENDED", "LOGICALLY_DELETED"})
    @DisplayName("El documento sigue reservado en todos los estados")
    void rejectsDuplicateInEveryStatus(String status) throws Exception {
        String body = objectMapper.writeValueAsString(new CreateUserRequest("DNI", "12345678", "Ana",
                "ana@example.com", null, LocalDate.of(1990, 1, 2)));
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        User stored = userRepository.findAll().getFirst();
        stored.setStatus(UserStatus.valueOf(status));
        userRepository.saveAndFlush(stored);

        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(auditRepository.count()).isEqualTo(1);
    }
}
