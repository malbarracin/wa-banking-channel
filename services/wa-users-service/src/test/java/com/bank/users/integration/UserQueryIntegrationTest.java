package com.bank.users.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:userqueries;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("TASK-5: consultas HTTP con servicio, mapper y persistencia reales")
class UserQueryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("Cada estado devuelve el contrato completo y su elegibilidad sin JWT ni escrituras")
    void queriesPreserveUserInEveryState(UserStatus userStatus) throws Exception {
        // Given: un usuario persistido, sin transacción de test que oculte escrituras HTTP.
        User user = persist(DocumentType.DNI, "state-" + userStatus, userStatus);
        try {
            Map<String, Object> before = jdbc.queryForMap("select * from users where id = ?", user.getId());
            long countBefore = repository.count();
            JsonNode expected = objectMapper.createObjectNode()
                    .put("id", user.getId())
                    .put("documentType", "DNI")
                    .put("documentNumber", user.getDocumentNumber())
                    .put("firstName", "Juan")
                    .put("lastName", "Perez")
                    .put("displayName", "Juancho")
                    .put("email", "juan@example.com")
                    .put("birthDate", "1990-05-20")
                    .put("status", userStatus.name())
                    .put("createdAt", user.getCreatedAt().toString())
                    .put("updatedAt", user.getUpdatedAt().toString());

            // When: las tres consultas atraviesan toda la aplicación sin Authorization.
            JsonNode byId = response(get("/api/v1/users/{id}", user.getId()));
            JsonNode byDocument = response(get("/api/v1/users")
                    .param("documentType", "DNI").param("documentNumber", user.getDocumentNumber()));
            JsonNode eligibility = response(get("/api/v1/users/{id}/eligible", user.getId()));

            // Then: igualdad exacta excluye campos de auditoría, historial y canal.
            assertThat(byId).isEqualTo(objectMapper.readTree(expected.toString()));
            assertThat(byDocument).isEqualTo(objectMapper.readTree(expected.toString()));
            JsonNode expectedEligibility = objectMapper.createObjectNode()
                    .put("userId", user.getId()).put("eligible", userStatus == UserStatus.ACTIVE);
            assertThat(eligibility).isEqualTo(objectMapper.readTree(expectedEligibility.toString()));
            assertThat(jdbc.queryForMap("select * from users where id = ?", user.getId())).isEqualTo(before);
            assertThat(repository.count()).isEqualTo(countBefore);
        } finally {
            repository.deleteById(user.getId());
        }
    }

    @Test
    @DisplayName("La consulta distingue usuarios con igual número y diferente tipo de documento")
    void documentLookupUsesBothComponents() throws Exception {
        // Given
        User dni = persist(DocumentType.DNI, "shared-number", UserStatus.ACTIVE);
        User passport = persist(DocumentType.PASAPORTE, "shared-number", UserStatus.INACTIVE);
        try {
            // When / Then
            for (User user : new User[] {dni, passport}) {
                JsonNode result = response(get("/api/v1/users")
                        .param("documentType", user.getDocumentType().name())
                        .param("documentNumber", "shared-number"));
                assertThat(result.path("id").asLong()).isEqualTo(user.getId());
                assertThat(result.path("status").asText()).isEqualTo(user.getStatus().name());
            }
            assertError(get("/api/v1/users").param("documentType", "CUIT")
                    .param("documentNumber", "shared-number"), 404, "NOT_FOUND");
        } finally {
            repository.deleteById(dni.getId());
            repository.deleteById(passport.getId());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users/9223372036854775807",
            "/api/v1/users/9223372036854775807/eligible",
            "/api/v1/users?documentType=DNI&documentNumber=absent"})
    @DisplayName("Las tres consultas de usuarios ausentes devuelven el error uniforme 404")
    void missingUsersReturnNotFound(String uri) throws Exception {
        // Given / When / Then
        assertError(get(uri), 404, "NOT_FOUND");
    }

    @ParameterizedTest
    @CsvSource({
            "/api/v1/users, VALIDATION_ERROR",
            "/api/v1/users?documentType=DNI, VALIDATION_ERROR",
            "/api/v1/users?documentNumber=1, VALIDATION_ERROR",
            "/api/v1/users?documentType=DNI&documentNumber=, VALIDATION_ERROR",
            "/api/v1/users?documentType=LIBRETA&documentNumber=1, BAD_REQUEST",
            "/api/v1/users/abc, BAD_REQUEST",
            "/api/v1/users/9223372036854775808, BAD_REQUEST",
            "/api/v1/users/abc/eligible, BAD_REQUEST"
    })
    @DisplayName("Parámetros ausentes o inválidos devuelven 400 con el error contract")
    void invalidQueriesReturnBadRequest(String uri, String code) throws Exception {
        // Given / When / Then
        assertError(get(uri), 400, code);
    }

    private JsonNode response(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void assertError(MockHttpServletRequestBuilder request, int statusCode, String code) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.code").value(code))
                .andReturn().getResponse().getContentAsString();
        JsonNode error = objectMapper.readTree(body);
        assertThat(error.size()).isEqualTo(4);
        assertThat(error.path("message").asText()).isNotBlank();
        assertThat(error.path("details").isArray()).isTrue();
        assertThat(error.path("timestamp").asText()).isNotBlank();
    }

    private User persist(DocumentType type, String number, UserStatus userStatus) {
        User user = new User();
        user.setDocumentType(type);
        user.setDocumentNumber(number);
        user.setFirstName("Juan");
        user.setLastName("Perez");
        user.setDisplayName("Juancho");
        user.setEmail("juan@example.com");
        user.setBirthDate(LocalDate.of(1990, 5, 20));
        user.setStatus(userStatus);
        User saved = repository.saveAndFlush(user);
        // Leer los instantes con la precisión almacenada por la base de datos.
        return repository.findById(saved.getId()).orElseThrow();
    }
}
