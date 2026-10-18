package com.bank.users.user.controller;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.bank.users.audit.entity.AuditAction;
import com.bank.users.audit.repository.AuditLogRepository;
import com.bank.users.audit.service.AuditService;
import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class UpdateUserIntegrationTest {

    private final MockMvc mockMvc;
    private final UserRepository repository;
    private final AuditLogRepository auditRepository;

    @MockitoSpyBean
    private AuditService auditService;

    private UUID userId;

    UpdateUserIntegrationTest(MockMvc mockMvc, UserRepository repository, AuditLogRepository auditRepository) {
        this.mockMvc = mockMvc;
        this.repository = repository;
        this.auditRepository = auditRepository;
    }

    @BeforeEach
    void createUser() {
        auditRepository.deleteAll();
        repository.deleteAll();
        User user = new User();
        user.setDocumentType(DocumentType.DNI);
        user.setDocumentNumber("12345678");
        user.setDisplayName("Ana");
        user.setEmail("ana@example.com");
        user.setPhone("+5491112345678");
        user.setBirthDate(LocalDate.of(1990, 1, 2));
        user.setPreferences(Map.of("language", "es"));
        userId = repository.saveAndFlush(user).getId();
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"ACTIVE", "SUSPENDED"})
    @DisplayName("Actualiza activos y suspendidos, conserva omitidos y audita sin valores personales")
    void updatesAllowedStatuses(UserStatus userStatus) throws Exception {
        User user = persisted();
        user.setStatus(userStatus);
        repository.saveAndFlush(user);

        mockMvc.perform(patch("/api/v1/users/{id}", userId).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Nuevo nombre","preferences":{
                                  "arbitraryKey":{"enabled":true},"values":[1,"x",null]}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Nuevo nombre"))
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.phone").value("+5491112345678"))
                .andExpect(jsonPath("$.status").value(userStatus.name()))
                .andExpect(jsonPath("$.preferences.arbitraryKey.enabled").value(true));

        User updated = persisted();
        assertThat(updated.getDisplayName()).isEqualTo("Nuevo nombre");
        assertThat(updated.getDocumentNumber()).isEqualTo("12345678");
        assertThat(updated.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 2));
        assertThat(updated.getPreferences()).containsKey("arbitraryKey").doesNotContainKey("language");
        assertThat(auditRepository.findAll()).singleElement().satisfies(audit -> {
            assertThat(audit.getAction()).isEqualTo(AuditAction.USER_UPDATED);
            assertThat(audit.getUserId()).isEqualTo(userId);
            assertThat(audit.getResult()).isEqualTo("SUCCESS");
            assertThat(audit.getOccurredAt()).isNotNull();
            assertThat(audit.getDetail()).isEqualTo("fields=displayName,preferences");
        });
    }

    @Test
    @DisplayName("Null elimina un contacto conservando el otro y vacía preferencias")
    void clearsExplicitNulls() throws Exception {
        patchSuccessfully("{\"email\":null,\"preferences\":null}");

        assertThat(persisted().getEmail()).isNull();
        assertThat(persisted().getPhone()).isEqualTo("+5491112345678");
        assertThat(persisted().getPreferences()).isEmpty();
        patchSuccessfully("{\"email\":\"new@example.com\",\"phone\":null}");
        assertThat(persisted().getEmail()).isEqualTo("new@example.com");
        assertThat(persisted().getPhone()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"displayName\":\"Ana\"}", "{\"preferences\":{\"language\":\"es\"}}"})
    @DisplayName("Un patch sin cambios relevantes no crea auditoría")
    void ignoresNoOp(String body) throws Exception {
        patchSuccessfully(body);

        assertThat(auditRepository.count()).isZero();
        assertThat(persisted().getDisplayName()).isEqualTo("Ana");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"displayName\":null}", "{\"displayName\":\" \"}", "{\"email\":\"invalid\"}",
        "{\"email\":\"\"}", "{\"phone\":\"123\"}", "{\"email\":null,\"phone\":null}",
        "{\"preferences\":[]}", "{\"email\":123}", "{\"displayName\":true}"})
    @DisplayName("Valores inválidos y pérdida de contacto no persisten cambios parciales")
    void rejectsInvalidValues(String invalidFields) throws Exception {
        String body = "{\"preferences\":{\"changed\":true}," + invalidFields.substring(1);
        mockMvc.perform(patch("/api/v1/users/{id}", userId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists());

        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"displayName", "email", "phone"})
    @DisplayName("Los límites de longitud se validan antes de persistir el patch")
    void rejectsOversizedValues(String field) throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}", userId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"" + field + "\":\"" + "a".repeat(255) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "\"text\"", "{", ""})
    @DisplayName("Un cuerpo ausente, no objeto o mal formado devuelve BAD_REQUEST sin cambios")
    void rejectsMalformedBodies(String body) throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}", userId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"documentType", "documentNumber", "document", "id", "birthDate", "status",
        "legalAddress", "maritalStatus", "channelLinkEligible", "unknown", "fields", "contactPresent"})
    @DisplayName("Toda propiedad fuera del contrato se rechaza identificando el campo")
    void rejectsForbiddenFields(String field) throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}", userId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Cambio\",\"" + field + "\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details[0].field").value(field))
                .andExpect(jsonPath("$.details[0].message").value("Campo no actualizable"))
                .andExpect(jsonPath("$.timestamp").exists());

        assertUnchanged();
    }

    @Test
    @DisplayName("Usuario inexistente devuelve 404 y baja lógica devuelve 409 sin modificaciones")
    void rejectsMissingAndDeleted() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        User user = persisted();
        user.setStatus(UserStatus.LOGICALLY_DELETED);
        repository.saveAndFlush(user);

        mockMvc.perform(patch("/api/v1/users/{id}", userId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Cambio\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details").isArray()).andExpect(jsonPath("$.timestamp").exists());
        assertUnchanged();
    }

    @Test
    @DisplayName("Un fallo después de insertar auditoría revierte auditoría y usuario ya enviados a la BD")
    void rollsBackAfterAuditFailure() throws Exception {
        AuditService auditTarget = AopTestUtils.getUltimateTargetObject(auditService);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            auditRepository.flush();
            throw new IllegalStateException("audit unavailable");
        }).when(auditTarget).recordUserUpdated(any(), any());

        mockMvc.perform(patch("/api/v1/users/{id}", userId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Cambio\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertUnchanged();
    }

    @ParameterizedTest
    @CsvSource({"email,ana@example.com,", "phone,,+5491112345678"})
    @DisplayName("Eliminar el único contacto se rechaza aunque el otro campo se omita")
    void rechazaEliminarElUnicoContacto(String contactField, String email, String phone) throws Exception {
        User user = persisted();
        user.setEmail(email);
        user.setPhone(phone);
        repository.saveAndFlush(user);

        mockMvc.perform(patch("/api/v1/users/{id}", userId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Cambio\",\"" + contactField + "\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("contact"))
                .andExpect(jsonPath("$.timestamp").exists());

        User unchanged = persisted();
        assertThat(unchanged.getDisplayName()).isEqualTo("Ana");
        assertThat(unchanged.getEmail()).isEqualTo(user.getEmail());
        assertThat(unchanged.getPhone()).isEqualTo(user.getPhone());
        assertThat(auditRepository.count()).isZero();
    }

    @Test
    @DisplayName("Sustituye el único contacto atómicamente y audita solo los nombres de campos")
    void sustituyeElUnicoContactoSinAuditarValores() throws Exception {
        User user = persisted();
        user.setPhone(null);
        repository.saveAndFlush(user);

        mockMvc.perform(patch("/api/v1/users/{id}", userId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":null,\"phone\":\"+34911234567\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+34911234567"));

        assertThat(persisted().getEmail()).isNull();
        assertThat(persisted().getPhone()).isEqualTo("+34911234567");
        assertThat(persisted().getPreferences()).containsExactlyEntriesOf(Map.of("language", "es"));
        assertThat(auditRepository.findAll()).singleElement().satisfies(audit -> {
            assertThat(audit.getAction()).isEqualTo(AuditAction.USER_UPDATED);
            assertThat(audit.getUserId()).isEqualTo(userId);
            assertThat(audit.getDetail()).isEqualTo("fields=email,phone");
        });
    }

    private void patchSuccessfully(String body) throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}", userId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private User persisted() {
        return repository.findById(userId).orElseThrow();
    }

    private void assertUnchanged() {
        User user = persisted();
        assertThat(user.getDisplayName()).isEqualTo("Ana");
        assertThat(user.getEmail()).isEqualTo("ana@example.com");
        assertThat(user.getPhone()).isEqualTo("+5491112345678");
        assertThat(user.getPreferences()).containsExactlyEntriesOf(Map.of("language", "es"));
        assertThat(auditRepository.count()).isZero();
    }
}
