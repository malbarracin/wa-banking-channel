package com.bcra.users.ws.user.controller;

import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bcra.users.ws.user.entity.AuditAction;
import com.bcra.users.ws.user.entity.AuditResult;
import com.bcra.users.ws.user.entity.UserStatus;
import com.bcra.users.ws.user.repository.AuditLogRepository;
import com.bcra.users.ws.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CreateUserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void limpiarRegistros() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "telefono", "ambos"})
    @DisplayName("Crea y audita el usuario activo con cualquiera de los contactos")
    void creaUsuarioActivoConContacto(String contacto) throws Exception {
        ObjectNode solicitud = solicitudValida();
        solicitud.remove("email");
        solicitud.remove("telefono");
        switch (contacto) {
            case "email" -> solicitud.put("email", "contacto libre");
            case "telefono" -> solicitud.put("telefono", "teléfono libre");
            default -> {
                solicitud.put("email", "contacto libre");
                solicitud.put("telefono", "teléfono libre");
            }
        }
        Instant inicio = Instant.now();

        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.documento.tipo").value("DNI"))
                .andExpect(jsonPath("$.documento.numero").value("123"))
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.apellido").value("Pérez"))
                .andExpect(jsonPath("$.fechaNacimiento").value("1990-01-01"))
                .andExpect(jsonPath("$.estado").value("ACTIVE"));

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.findByDocumentoTipoAndDocumentoNumero("DNI", "123")).hasValueSatisfying(
                usuario -> assertThat(usuario.getEstado()).isEqualTo(UserStatus.ACTIVE));
        assertThat(auditLogRepository.findAll()).singleElement().satisfies(registro -> {
            assertThat(registro.getAccion()).isEqualTo(AuditAction.ALTA);
            assertThat(registro.getResultado()).isEqualTo(AuditResult.EXITOSO);
            assertThat(registro.getFecha()).isBetween(inicio, Instant.now());
            assertThat(registro.getTipoDocumento()).isEqualTo("DNI");
            assertThat(registro.getNumeroDocumento()).isEqualTo("123");
        });
    }

    static Stream<String> camposIncompletos() {
        return Stream.of("documento", "tipo", "numero", "nombre", "apellido", "fechaNacimiento",
                "contactoNulo", "contactoVacio", "contactoBlanco", "nombreBlanco", "tipoBlanco", "numeroBlanco");
    }

    @ParameterizedTest
    @MethodSource("camposIncompletos")
    @DisplayName("Rechaza los datos mínimos incompletos sin persistir usuarios")
    void rechazaDatosMinimosIncompletos(String campo) throws Exception {
        ObjectNode solicitud = solicitudValida();
        switch (campo) {
            case "tipo", "numero" -> ((ObjectNode) solicitud.get("documento")).remove(campo);
            case "contactoNulo" -> {
                solicitud.putNull("email");
                solicitud.putNull("telefono");
            }
            case "contactoVacio" -> {
                solicitud.put("email", "");
                solicitud.put("telefono", "");
            }
            case "contactoBlanco" -> {
                solicitud.put("email", "  ");
                solicitud.put("telefono", "  ");
            }
            case "nombreBlanco" -> solicitud.put("nombre", "  ");
            case "tipoBlanco" -> ((ObjectNode) solicitud.get("documento")).put("tipo", "  ");
            case "numeroBlanco" -> ((ObjectNode) solicitud.get("documento")).put("numero", "  ");
            default -> solicitud.remove(campo);
        }

        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").isNotEmpty())
                .andExpect(jsonPath("$.details[0].message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        assertThat(userRepository.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("Rechaza y audita documentos duplicados en todos los estados")
    void rechazaDocumentoExistenteEnCualquierEstado(UserStatus estado) throws Exception {
        String solicitud = solicitudValida().toString();
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(solicitud))
                .andExpect(status().isCreated());
        var usuario = userRepository.findByDocumentoTipoAndDocumentoNumero("DNI", "123").orElseThrow();
        usuario.setEstado(estado);
        userRepository.saveAndFlush(usuario);

        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(solicitud))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Ya existe un usuario con ese documento (duplicado)"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(auditLogRepository.findAll()).hasSize(2).anySatisfy(registro -> {
            assertThat(registro.getResultado()).isEqualTo(AuditResult.RECHAZADO);
            assertThat(registro.getAccion()).isEqualTo(AuditAction.ALTA);
            assertThat(registro.getFecha()).isNotNull();
            assertThat(registro.getMotivo()).isEqualTo("Documento duplicado");
            assertThat(registro.getTipoDocumento()).isEqualTo("DNI");
            assertThat(registro.getNumeroDocumento()).isEqualTo("123");
        });
    }

    @ParameterizedTest
    @CsvSource({"PASAPORTE, 123", "DNI, 456"})
    @DisplayName("Permite repetir un componente del documento cuando el par es diferente")
    void permiteDocumentoConTipoONumeroDiferente(String tipo, String numero) throws Exception {
        ObjectNode solicitud = solicitudValida();
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud.toString()))
                .andExpect(status().isCreated());
        ObjectNode documento = (ObjectNode) solicitud.get("documento");
        documento.put("tipo", tipo);
        documento.put("numero", numero);

        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documento.tipo").value(tipo))
                .andExpect(jsonPath("$.documento.numero").value(numero))
                .andExpect(jsonPath("$.estado").value("ACTIVE"));

        assertThat(userRepository.count()).isEqualTo(2);
        assertThat(userRepository.findByDocumentoTipoAndDocumentoNumero(tipo, numero)).isPresent();
        assertThat(auditLogRepository.findAll()).hasSize(2).allSatisfy(registro -> {
            assertThat(registro.getAccion()).isEqualTo(AuditAction.ALTA);
            assertThat(registro.getResultado()).isEqualTo(AuditResult.EXITOSO);
        });
    }

    @ParameterizedTest
    @CsvSource({"'  ', contacto", "contacto, '  '", "'', contacto", "contacto, ''"})
    @DisplayName("Acepta un contacto presente aunque el otro esté vacío y conserva ambos valores")
    void aceptaUnContactoAunqueElOtroEsteVacio(String email, String telefono) throws Exception {
        ObjectNode solicitud = solicitudValida();
        solicitud.put("email", email);
        solicitud.put("telefono", telefono);

        String respuesta = mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.telefono").value(telefono))
                .andReturn().getResponse().getContentAsString();

        var usuario = userRepository.findByDocumentoTipoAndDocumentoNumero("DNI", "123").orElseThrow();
        assertThat(usuario.getEmail()).isEqualTo(email);
        assertThat(usuario.getTelefono()).isEqualTo(telefono);
        assertThat(objectMapper.readTree(respuesta).get("id").asText()).isEqualTo(usuario.getId().toString());
    }

    private ObjectNode solicitudValida() throws Exception {
        return (ObjectNode) objectMapper.readTree("""
                {
                  "documento": {"tipo": "DNI", "numero": "123"},
                  "nombre": "Ana", "apellido": "Pérez", "fechaNacimiento": "1990-01-01",
                  "email": "contacto libre", "telefono": null
                }
                """);
    }
}
