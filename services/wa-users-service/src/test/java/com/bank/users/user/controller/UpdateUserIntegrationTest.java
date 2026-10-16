package com.bank.users.user.controller;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.bank.users.audit.entity.AuditAction;
import com.bank.users.audit.entity.AuditDetail;
import com.bank.users.audit.entity.AuditResult;
import com.bank.users.audit.service.AuditService;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UpdateUserIntegrationTest {

    private final MockMvc mockMvc;
    private final UserRepository repository;
    private final AuditService auditService;
    private final JdbcTemplate jdbc;

    @Autowired
    UpdateUserIntegrationTest(MockMvc mockMvc, UserRepository repository, AuditService auditService,
            JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.repository = repository;
        this.auditService = auditService;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("PATCH persiste todos los campos permitidos y audita sin PII")
    void actualizaCamposPermitidosYAudita() throws Exception {
        User user = createUser();

        mockMvc.perform(patch("/api/v1/users/{id}", user.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombreTratamiento":"Nuevo","email":"nuevo@example.com",
                         "telefonoContacto":"987654321","preferencias":{"idioma":"en","avisos":true}}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreTratamiento").value("Nuevo"))
                .andExpect(jsonPath("$.email").value("nuevo@example.com"))
                .andExpect(jsonPath("$.telefonoContacto").value("987654321"))
                .andExpect(jsonPath("$.preferencias.idioma").value("en"))
                .andExpect(jsonPath("$.preferencias.avisos").value(true));

        User saved = repository.findById(user.getId()).orElseThrow();
        assertThat(saved.getNombreTratamiento()).isEqualTo("Nuevo");
        assertThat(saved.getEmail()).isEqualTo("nuevo@example.com");
        assertThat(saved.getTelefonoContacto()).isEqualTo("987654321");
        assertThat(saved.getPreferencias()).containsExactlyInAnyOrderEntriesOf(Map.of("idioma", "en", "avisos", true));
        assertThat(saved.getStatus()).isEqualTo(UserStatus.SUSPENDIDO);
        assertThat(saved.getNumeroDocumento()).isEqualTo(user.getNumeroDocumento());
        assertThat(saved.getCreatedAt()).isEqualTo(user.getCreatedAt());
        assertThat(auditService.findHistory(user.getId())).singleElement().satisfies(entry -> {
            assertThat(entry.getAction()).isEqualTo(AuditAction.ACTUALIZACION);
            assertThat(entry.getResult()).isEqualTo(AuditResult.OK);
            assertThat(entry.getDetail()).isEqualTo(AuditDetail.OPERACION_CONFIRMADA.name());
            assertThat(entry.getOccurredAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("Los campos omitidos se preservan y un mapa vacío elimina las preferencias")
    void preservaOmitidosYReemplazaPreferencias() throws Exception {
        User user = createUser();

        mockMvc.perform(patch("/api/v1/users/{id}", user.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"otro@example.com\"}"))
                .andExpect(status().isOk());

        User saved = repository.findById(user.getId()).orElseThrow();
        assertThat(saved.getNombreTratamiento()).isEqualTo(user.getNombreTratamiento());
        assertThat(saved.getTelefonoContacto()).isEqualTo(user.getTelefonoContacto());
        assertThat(saved.getPreferencias()).isEqualTo(user.getPreferencias());

        mockMvc.perform(patch("/api/v1/users/{id}", user.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferencias\":{}}"))
                .andExpect(status().isOk());

        assertThat(repository.findById(user.getId()).orElseThrow().getPreferencias()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"documento", "tipoDocumento", "numeroDocumento", "id", "status",
            "domicilioLegal", "estadoCivil", "createdAt", "updatedAt", "desconocido"})
    @DisplayName("Los campos ajenos al DTO rechazan todo el body sin mutar ni auditar un alta")
    void rechazaCamposAjenosSinMutacion(String field) throws Exception {
        User user = createUser();
        long auditBefore = jdbc.queryForObject("select count(*) from audit_entries", Long.class);

        mockMvc.perform(patch("/api/v1/users/{id}", user.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreTratamiento\":\"Cambio\",\"" + field + "\":\"prohibido\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(repository.findById(user.getId()).orElseThrow().getNombreTratamiento()).isEqualTo("Cliente");
        assertThat(auditService.findHistory(user.getId())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from audit_entries", Long.class)).isEqualTo(auditBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"nombreTratamiento\":\" \"}", "{\"email\":\"invalido\"}",
            "{\"email\":null}", "{\"nombreTratamiento\":null}", "{\"telefonoContacto\":null}",
            "{\"preferencias\":null}", "{\"preferencias\":[]}", "null", "{", ""})
    @DisplayName("Un body vacío o inválido conserva el usuario y devuelve el error uniforme")
    void rechazaBodyInvalido(String body) throws Exception {
        User user = createUser();

        mockMvc.perform(patch("/api/v1/users/{id}", user.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.isOneOf("BAD_REQUEST", "VALIDATION_ERROR")))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(repository.findById(user.getId()).orElseThrow().getNombreTratamiento()).isEqualTo("Cliente");
        assertThat(auditService.findHistory(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("El usuario inexistente devuelve 404 y un UUID inválido devuelve 400")
    void rechazaIdentificadoresInvalidos() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreTratamiento\":\"Nuevo\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
        mockMvc.perform(patch("/api/v1/users/invalido").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreTratamiento\":\"Nuevo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"email\":\"nuevo@example.com\",\"nombreTratamiento\":42}",
            "{\"nombreTratamiento\":\"Cambio\",\"email\":true}",
            "{\"nombreTratamiento\":\"Cambio\",\"telefonoContacto\":[]}",
            "{\"nombreTratamiento\":\"Cambio\",\"preferencias\":\"es\"}",
            "{\"nombreTratamiento\":\"Cambio\",\"preferencias\":{\"idioma\":null}}"})
    @DisplayName("Los tipos incorrectos rechazan también los cambios válidos del mismo body")
    void rechazaTiposInvalidosSinAplicarCambios(String body) throws Exception {
        User user = createUser();
        long auditBefore = jdbc.queryForObject("select count(*) from audit_entries", Long.class);

        mockMvc.perform(patch("/api/v1/users/{id}", user.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertThat(repository.findById(user.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(user);
        assertThat(jdbc.queryForObject("select count(*) from audit_entries", Long.class)).isEqualTo(auditBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nombreTratamiento", "email", "telefonoContacto"})
    @DisplayName("El límite de longitud se valida antes de persistir cualquier cambio")
    void rechazaTextoQueSuperaElLimiteSinMutacion(String field) throws Exception {
        User user = createUser();
        String value = "a".repeat(256) + "@example.com";
        long auditBefore = jdbc.queryForObject("select count(*) from audit_entries", Long.class);

        mockMvc.perform(patch("/api/v1/users/{id}", user.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferencias\":{\"idioma\":\"en\"},\"" + field + "\":\"" + value + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[*].field").value(org.hamcrest.Matchers.hasItem(field)))
                .andExpect(jsonPath("$.timestamp").isString());

        assertThat(repository.findById(user.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(user);
        assertThat(jdbc.queryForObject("select count(*) from audit_entries", Long.class)).isEqualTo(auditBefore);
    }

    private User createUser() {
        User user = new User();
        user.setTipoDocumento("DNI");
        user.setNumeroDocumento(UUID.randomUUID().toString());
        user.setNombreTratamiento("Cliente");
        user.setEmail("cliente@example.com");
        user.setTelefonoContacto("123456789");
        user.setPreferencias(Map.of("idioma", "es"));
        user.setStatus(UserStatus.SUSPENDIDO);
        return repository.saveAndFlush(user);
    }
}
