package com.bank.users.user.controller;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FindUserIntegrationTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository repository;

    FindUserIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, UserRepository repository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("Ambos identificadores devuelven los mismos datos autorizados y la aptitud según estado")
    void findsBothIdentifiersInEveryStatus(UserStatus userStatus) throws Exception {
        User user = new User();
        user.setDocumentType(DocumentType.DNI);
        user.setDocumentNumber("12345678");
        user.setDisplayName("Ana");
        user.setEmail("ana@example.com");
        user.setBirthDate(LocalDate.of(1990, 1, 2));
        user.setStatus(userStatus);
        user.setPreferences(Map.of("language", "es"));
        repository.saveAndFlush(user);
        // Mismo número con otro tipo: la búsqueda debe usar la clave completa.
        User other = new User();
        other.setDocumentType(DocumentType.PASSPORT);
        other.setDocumentNumber("12345678");
        other.setDisplayName("Otra persona");
        other.setBirthDate(LocalDate.of(1991, 1, 2));
        repository.saveAndFlush(other);

        String byId = mockMvc.perform(get("/api/v1/users/{id}", user.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String byDocument = mockMvc.perform(get("/api/v1/users")
                        .param("documentType", "DNI").param("documentNumber", "12345678"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode response = objectMapper.readTree(byId);
        assertThat(objectMapper.readTree(byDocument)).isEqualTo(response);
        assertThat(response.get("id").asText()).isEqualTo(user.getId().toString());
        assertThat(response.get("status").asText()).isEqualTo(userStatus.name());
        assertThat(response.get("channelLinkEligible").asBoolean()).isEqualTo(userStatus == UserStatus.ACTIVE);
        assertThat(response.get("preferences").get("language").asText()).isEqualTo("es");
        Set<String> fields = new HashSet<>();
        response.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "documentType", "documentNumber", "displayName",
                "email", "phone", "birthDate", "status", "preferences", "channelLinkEligible");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users/00000000-0000-0000-0000-000000000000",
        "/api/v1/users?documentType=DNI&documentNumber=99999999"})
    @DisplayName("Ambas consultas inexistentes devuelven el contrato NOT_FOUND sin datos personales")
    void returnsNotFound(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No existe el usuario solicitado"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users/not-a-uuid", "/api/v1/users",
        "/api/v1/users?documentType=DNI", "/api/v1/users?documentNumber=123",
        "/api/v1/users?documentType=OTHER&documentNumber=123",
        "/api/v1/users?documentType=&documentNumber=123"})
    @DisplayName("UUID inválido y parámetros ausentes o de tipo inválido devuelven BAD_REQUEST")
    void rejectsInvalidIdentifiers(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "12-34", "ñ123",
        "12345678901234567890123456789012345678901234567890123456789012345"})
    @DisplayName("Número vacío o fuera del formato del alta devuelve VALIDATION_ERROR")
    void rejectsInvalidDocumentNumber(String documentNumber) throws Exception {
        mockMvc.perform(get("/api/v1/users").param("documentType", "DNI").param("documentNumber", documentNumber))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
