package com.bank.users.user.controller;

import java.time.Instant;

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
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.bank.users.audit.repository.AuditLogRepository;
import com.bank.users.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CreateUserValidationIntegrationTest {

    private static final String USERS_ENDPOINT = "/api/v1/users";
    private static final String VALID_REQUEST = """
            {
              "documentType": "DNI",
              "documentNumber": "87654321",
              "displayName": "Persona de prueba",
              "email": "persona@example.com",
              "phone": "+5491112345678",
              "birthDate": "1990-01-02"
            }
            """;

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final AuditLogRepository auditRepository;

    CreateUserValidationIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper,
            UserRepository userRepository, AuditLogRepository auditRepository) {
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
    @CsvSource(value = {
        "documentType,OTHER",
        "documentType,dni",
        "documentType,NULL",
        "documentType,''",
        "documentNumber,NULL",
        "documentNumber,''",
        "documentNumber,'   '",
        "documentNumber,12/34",
        "displayName,NULL",
        "displayName,''",
        "displayName,'   '",
        "birthDate,NULL",
        "birthDate,2990-01-02",
        "email,invalid",
        "email,''",
        "email,'   '",
        "phone,invalid",
        "phone,''",
        "phone,'   '"
    }, nullValues = "NULL")
    @DisplayName("Cada campo inválido se rechaza aunque los demás campos sean válidos")
    void rejectsEachInvalidFieldIndependently(String field, String value) throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(VALID_REQUEST);
        request.put(field, value);

        assertRejectedWithoutPersistence(request, field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"documentType", "documentNumber", "displayName", "birthDate"})
    @DisplayName("Cada dato mínimo omitido impide el alta sin generar auditoría")
    void rejectsEachMissingRequiredField(String field) throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(VALID_REQUEST);
        request.remove(field);

        assertRejectedWithoutPersistence(request, field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"documentNumber", "displayName", "email", "phone"})
    @DisplayName("Un campo de longitud excesiva devuelve validación sin llegar a persistencia")
    void rejectsOversizedFields(String field) throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(VALID_REQUEST);
        request.put(field, "1".repeat(300));

        assertRejectedWithoutPersistence(request, field);
    }

    private void assertRejectedWithoutPersistence(ObjectNode request, String field) throws Exception {
        String body = mockMvc.perform(post(USERS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isString())
                .andReturn().getResponse().getContentAsString();

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.get("details").findValuesAsText("field")).contains(field);
        assertThat(Instant.parse(response.get("timestamp").asText())).isNotNull();
        assertThat(body).doesNotContain("87654321", "persona@example.com", "+5491112345678",
                "Persona de prueba", "stackTrace", "org.hibernate");
        assertThat(userRepository.count()).isZero();
        assertThat(auditRepository.count()).isZero();
    }
}
