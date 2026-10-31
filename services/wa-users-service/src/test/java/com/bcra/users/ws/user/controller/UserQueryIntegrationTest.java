package com.bcra.users.ws.user.controller;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.users.ws.user.entity.Documento;
import com.bcra.users.ws.user.entity.User;
import com.bcra.users.ws.user.entity.UserStatus;
import com.bcra.users.ws.user.repository.UserRepository;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserQueryIntegrationTest {

    private final MockMvc mockMvc;
    private final UserRepository repository;

    @Autowired
    UserQueryIntegrationTest(MockMvc mockMvc, UserRepository repository) {
        this.mockMvc = mockMvc;
        this.repository = repository;
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("Consulta el documento exacto y devuelve todos los datos y el estado actual")
    void consultaUsuarioExistente(UserStatus estado) throws Exception {
        User usuario = guardarUsuario("TIPO_ABIERTO", "12345678", estado);
        guardarUsuario("OTRO", "12345678", UserStatus.ACTIVE);
        guardarUsuario("TIPO_ABIERTO", "87654321", UserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/users/{tipo}/{numero}", "TIPO_ABIERTO", "12345678")
                        .principal(() -> "servicio-autorizado")
                        .with(request -> { request.addUserRole("USERS_FULL_READ"); return request; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(10)))
                .andExpect(jsonPath("$.id").value(usuario.getId().toString()))
                .andExpect(jsonPath("$.documento.tipo").value("TIPO_ABIERTO"))
                .andExpect(jsonPath("$.documento.numero").value("12345678"))
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.apellido").value("Pérez"))
                .andExpect(jsonPath("$.fechaNacimiento").value("1990-01-15"))
                .andExpect(jsonPath("$.email").value("ana@example.test"))
                .andExpect(jsonPath("$.telefono").value("555123456"))
                .andExpect(jsonPath("$.nombreTratamiento").value("Anita"))
                .andExpect(jsonPath("$.preferencias").value("idioma: español"))
                .andExpect(jsonPath("$.estado").value(estado.name()));
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("La consulta de estado incluye suspensión y baja lógica sin exponer otros datos")
    void consultaEstadoActual(UserStatus estado) throws Exception {
        guardarUsuario("DNI", "12345678", estado);

        mockMvc.perform(get("/api/v1/users/{tipo}/{numero}/status", "DNI", "12345678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.documento.tipo").value("DNI"))
                .andExpect(jsonPath("$.documento.numero").value("12345678"))
                .andExpect(jsonPath("$.estado").value(estado.name()));
    }

    @Test
    @DisplayName("Un documento inexistente devuelve 404 sin datos de otros usuarios")
    void consultaInexistenteSinExponerDatos() throws Exception {
        guardarUsuario("DNI", "12345678", UserStatus.ACTIVE);

        verificarNoEncontrado("/api/v1/users/OTRO/12345678");
        verificarNoEncontrado("/api/v1/users/DNI/87654321");
    }

    @Test
    @DisplayName("La consulta de estado de un documento inexistente devuelve 404")
    void consultaEstadoInexistente() throws Exception {
        guardarUsuario("DNI", "12345678", UserStatus.SUSPENDED);

        verificarNoEncontrado("/api/v1/users/OTRO/12345678/status");
        verificarNoEncontrado("/api/v1/users/DNI/87654321/status");
    }

    @Test
    @DisplayName("La consulta completa deniega anónimos y perfiles sin permiso sin exponer PII")
    void deniegaConsultaCompletaSinAutorizacion() throws Exception {
        guardarUsuario("DNI", "12345678", UserStatus.ACTIVE);
        for (String numero : new String[] {"12345678", "87654321"}) {
            mockMvc.perform(get("/api/v1/users/DNI/{numero}", numero)
                            .header("X-Profile", "USERS_FULL_READ"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$", aMapWithSize(4)))
                    .andExpect(jsonPath("$.details").isEmpty());
            mockMvc.perform(get("/api/v1/users/DNI/{numero}", numero)
                            .principal(() -> "soporte")
                            .with(request -> { request.addUserRole("SUPPORT"); return request; }))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.documento").doesNotExist());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "12345678, true, false",
        "87654321, true, false",
        "12345678, false, true",
        "87654321, false, true"
    })
    @DisplayName("Exige principal y rol conjuntamente aunque los headers declaren autorización")
    void deniegaCuandoFaltaPrincipalORol(String numero, boolean tienePrincipal, boolean tieneRol) throws Exception {
        guardarUsuario("DNI", "12345678", UserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/users/DNI/{numero}", numero)
                        .header("X-Profile", "USERS_FULL_READ")
                        .header("X-Role", "USERS_FULL_READ")
                        .with(request -> {
                            if (tienePrincipal) {
                                request.setUserPrincipal(() -> "consultante");
                            }
                            if (tieneRol) {
                                request.addUserRole("USERS_FULL_READ");
                            }
                            return request;
                        }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.documento").doesNotExist())
                .andExpect(jsonPath("$.fechaNacimiento").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.telefono").doesNotExist());
    }

    private void verificarNoEncontrado(String ruta) throws Exception {
        mockMvc.perform(get(ruta).principal(() -> "servicio-autorizado")
                        .with(request -> { request.addUserRole("USERS_FULL_READ"); return request; }))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Usuario no encontrado"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    private User guardarUsuario(String tipo, String numero, UserStatus estado) {
        Documento documento = new Documento();
        documento.setTipo(tipo);
        documento.setNumero(numero);
        User usuario = new User();
        usuario.setDocumento(documento);
        usuario.setNombre("Ana");
        usuario.setApellido("Pérez");
        usuario.setFechaNacimiento(LocalDate.of(1990, 1, 15));
        usuario.setEmail("ana@example.test");
        usuario.setTelefono("555123456");
        usuario.setNombreTratamiento("Anita");
        usuario.setPreferencias("idioma: español");
        usuario.setEstado(estado);
        return repository.saveAndFlush(usuario);
    }
}
