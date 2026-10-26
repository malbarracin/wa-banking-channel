package com.bcra.users.ws.user.controller;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import com.bcra.users.ws.user.repository.AuditLogRepository;
import com.bcra.users.ws.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class UserUpdateIntegrationTest {

    private final MockMvc mockMvc;
    private final UserRepository repository;
    private final AuditLogRepository auditoria;

    @BeforeEach
    void prepararUsuario() {
        auditoria.deleteAll();
        repository.deleteAll();
        Documento documento = new Documento();
        documento.setTipo("DNI");
        documento.setNumero("12345678");
        User usuario = new User();
        usuario.setDocumento(documento);
        usuario.setNombre("Ana");
        usuario.setApellido("Pérez");
        usuario.setFechaNacimiento(LocalDate.of(1990, 1, 15));
        usuario.setEmail("ana@example.test");
        usuario.setTelefono("555123456");
        repository.saveAndFlush(usuario);
    }

    @Test
    @DisplayName("Actualiza los cuatro campos permitidos, confirma persistencia y audita fecha y resultado")
    void actualizaDatosPermitidos() throws Exception {
        mockMvc.perform(patch("/api/v1/users/DNI/12345678").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"nuevo@example.test","telefono":"555999","nombreTratamiento":"Anita",
                 "preferencias":"idioma: español"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("nuevo@example.test"))
                .andExpect(jsonPath("$.telefono").value("555999"))
                .andExpect(jsonPath("$.nombreTratamiento").value("Anita"))
                .andExpect(jsonPath("$.preferencias").value("idioma: español"));

        User guardado = repository.findByDocumentoTipoAndDocumentoNumero("DNI", "12345678").orElseThrow();
        assertThat(guardado.getEmail()).isEqualTo("nuevo@example.test");
        assertThat(guardado.getTelefono()).isEqualTo("555999");
        assertThat(guardado.getNombreTratamiento()).isEqualTo("Anita");
        assertThat(guardado.getPreferencias()).isEqualTo("idioma: español");
        verificarAuditoria(AuditResult.EXITOSO);
    }

    @Test
    @DisplayName("Los campos omitidos y null conservan sus valores")
    void conservaCamposNoInformados() throws Exception {
        mockMvc.perform(patch("/api/v1/users/DNI/12345678").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreTratamiento\":\"Anita\",\"email\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@example.test"))
                .andExpect(jsonPath("$.telefono").value("555123456"));
        verificarAuditoria(AuditResult.EXITOSO);
    }

    @Test
    @DisplayName("Un documento inexistente devuelve 404 y audita el rechazo")
    void rechazaDocumentoInexistente() throws Exception {
        mockMvc.perform(patch("/api/v1/users/OTRO/12345678").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nuevo@example.test\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
        verificarAuditoria(AuditResult.RECHAZADO);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "\"documento\":{\"tipo\":\"PASAPORTE\",\"numero\":\"999\"}",
        "\"fechaNacimiento\":\"2000-01-01\"", "\"nombre\":\"Otro\"", "\"apellido\":\"Otro\"",
        "\"estado\":\"SUSPENDED\"", "\"domicilioLegal\":\"Otro\"", "\"fechaNacimiento\":null"
    })
    @DisplayName("Rechaza campos no editables sin aplicar siquiera los campos permitidos y audita")
    void rechazaCamposNoEditables(String propiedad) throws Exception {
        mockMvc.perform(patch("/api/v1/users/DNI/12345678").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nuevo@example.test\"," + propiedad + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("El campo no es editable por este servicio"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        User guardado = repository.findByDocumentoTipoAndDocumentoNumero("DNI", "12345678").orElseThrow();
        assertThat(guardado.getEmail()).isEqualTo("ana@example.test");
        assertThat(guardado.getFechaNacimiento()).isEqualTo(LocalDate.of(1990, 1, 15));
        assertThat(repository.count()).isEqualTo(1);
        verificarAuditoria(AuditResult.RECHAZADO);
    }

    @Test
    @DisplayName("Un JSON mal formado devuelve el contrato de error y queda auditado")
    void rechazaJsonInvalido() throws Exception {
        mockMvc.perform(patch("/api/v1/users/DNI/12345678").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verificarAuditoria(AuditResult.RECHAZADO);
    }

    private void verificarAuditoria(AuditResult resultado) {
        assertThat(auditoria.findAll()).singleElement().satisfies(registro -> {
            assertThat(registro.getAccion()).isEqualTo(AuditAction.ACTUALIZACION);
            assertThat(registro.getResultado()).isEqualTo(resultado);
            assertThat(registro.getFecha()).isNotNull();
            assertThat(registro.getNumeroDocumento()).isEqualTo("12345678");
        });
    }
}
