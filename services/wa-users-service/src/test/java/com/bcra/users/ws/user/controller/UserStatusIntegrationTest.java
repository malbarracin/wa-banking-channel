package com.bcra.users.ws.user.controller;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bcra.users.ws.user.entity.AuditAction;
import com.bcra.users.ws.user.entity.AuditResult;
import com.bcra.users.ws.user.entity.Documento;
import com.bcra.users.ws.user.entity.User;
import com.bcra.users.ws.user.entity.UserStatus;
import com.bcra.users.ws.user.repository.AuditLogRepository;
import com.bcra.users.ws.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class UserStatusIntegrationTest {

    private static final String RUTA = "/api/v1/users/DNI/12345678/status";

    private final MockMvc mockMvc;
    private final UserRepository repository;
    private final AuditLogRepository auditoria;

    @BeforeEach
    void limpiarDatos() {
        auditoria.deleteAll();
        repository.deleteAll();
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE,SUSPENDED", "SUSPENDED,ACTIVE", "ACTIVE,LOGICAL_DELETE", "SUSPENDED,LOGICAL_DELETE"})
    @DisplayName("Confirma cada transición soportada y registra auditoría con documento, fecha y resultado")
    void cambiaEstadoYAudita(UserStatus inicial, UserStatus solicitado) throws Exception {
        User original = guardarUsuario(inicial);
        Instant inicio = Instant.now();

        mockMvc.perform(patch(RUTA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"" + solicitado + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documento.tipo").value("DNI"))
                .andExpect(jsonPath("$.documento.numero").value("12345678"))
                .andExpect(jsonPath("$.estado").value(solicitado.name()));

        User guardado = repository.findById(original.getId()).orElseThrow();
        assertThat(guardado.getEstado()).isEqualTo(solicitado);
        assertThat(guardado).usingRecursiveComparison().ignoringFields("estado", "updatedAt").isEqualTo(original);
        mockMvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(solicitado.name()));
        verificarAuditoria(AuditResult.EXITOSO);
        assertThat(auditoria.findAll()).singleElement().satisfies(registro -> {
            assertThat(registro.getFecha()).isBetween(inicio.minusNanos(1000), Instant.now().plusNanos(1000));
            assertThat(registro.getMotivo()).isEmpty();
        });
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE,ACTIVE", "SUSPENDED,SUSPENDED", "LOGICAL_DELETE,ACTIVE",
        "LOGICAL_DELETE,SUSPENDED", "LOGICAL_DELETE,LOGICAL_DELETE"})
    @DisplayName("Rechaza todas las transiciones no soportadas con 400, sin cambios y con auditoría")
    void rechazaTransicionNoSoportada(UserStatus inicial, UserStatus solicitado) throws Exception {
        User original = guardarUsuario(inicial);

        mockMvc.perform(patch(RUTA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"" + solicitado + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        assertThat(repository.findById(original.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(original);
        verificarAuditoria(AuditResult.RECHAZADO);
        assertThat(auditoria.findAll()).singleElement().satisfies(registro ->
                assertThat(registro.getMotivo()).isNotBlank());
    }

    @Test
    @DisplayName("Devuelve 404 para un documento inexistente y conserva auditoría del rechazo")
    void rechazaDocumentoInexistente() throws Exception {
        mockMvc.perform(patch(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"SUSPENDED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        assertThat(repository.count()).isZero();
        verificarAuditoria(AuditResult.RECHAZADO);
        assertThat(auditoria.findAll()).singleElement().satisfies(registro ->
                assertThat(registro.getMotivo()).isNotBlank());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"estado\":null}", "{\"estado\":\"DESCONOCIDO\"}", "{", "null", ""})
    @DisplayName("Rechaza estados ausentes o inválidos y JSON ilegible con auditoría CAMBIO_ESTADO")
    void rechazaSolicitudInvalida(String contenido) throws Exception {
        User original = guardarUsuario(UserStatus.ACTIVE);

        mockMvc.perform(patch(RUTA).contentType(MediaType.APPLICATION_JSON).content(contenido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        assertThat(repository.findById(original.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(original);
        verificarAuditoria(AuditResult.RECHAZADO);
        assertThat(auditoria.findAll()).singleElement().satisfies(registro ->
                assertThat(registro.getMotivo()).isNotBlank());
    }

    private User guardarUsuario(UserStatus estado) {
        Documento documento = new Documento();
        documento.setTipo("DNI");
        documento.setNumero("12345678");
        User usuario = new User();
        usuario.setDocumento(documento);
        usuario.setNombre("Ana");
        usuario.setApellido("Pérez");
        usuario.setFechaNacimiento(LocalDate.of(1990, 1, 15));
        usuario.setEmail("ana@example.test");
        usuario.setEstado(estado);
        return repository.saveAndFlush(usuario);
    }

    private void verificarAuditoria(AuditResult resultado) {
        assertThat(auditoria.findAll()).singleElement().satisfies(registro -> {
            assertThat(registro.getAccion()).isEqualTo(AuditAction.CAMBIO_ESTADO);
            assertThat(registro.getResultado()).isEqualTo(resultado);
            assertThat(registro.getTipoDocumento()).isEqualTo("DNI");
            assertThat(registro.getNumeroDocumento()).isEqualTo("12345678");
            assertThat(registro.getFecha()).isNotNull();
        });
    }
}
