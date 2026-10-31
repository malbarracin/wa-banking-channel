package com.bcra.credentials.ws.credencial.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.bcra.credentials.ws.credencial.client.ClienteEstadoResponse;
import com.bcra.credentials.ws.credencial.client.ClienteEstadoResponse.EstadoCliente;
import com.bcra.credentials.ws.credencial.client.ClienteNoEncontradoException;
import com.bcra.credentials.ws.credencial.client.UsersServiceClient;
import com.bcra.credentials.ws.credencial.client.UsersServiceUnavailableException;
import com.bcra.credentials.ws.credencial.dto.ValidarCredencialRequest;
import com.bcra.credentials.ws.credencial.entity.AuditAccion;
import com.bcra.credentials.ws.credencial.entity.AuditResultado;
import com.bcra.credentials.ws.credencial.entity.CredencialEstado;
import com.bcra.credentials.ws.credencial.entity.MotivoRevocacion;
import com.bcra.credentials.ws.credencial.repository.CredencialAuditLogRepository;
import com.bcra.credentials.ws.credencial.repository.CredencialRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmisionIntegrationTest {

    private static final String NUMERO = "+5491123456789";
    private static final String REQUEST = """
            {"numeroWhatsapp":"+5491123456789","documentoTipo":"DNI","documentoNumero":"12345678",
             "vinculoActivo":true,"identidadVerificada":true}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CredencialRepository repository;
    @Autowired
    private CredencialAuditLogRepository auditRepository;
    @MockitoBean
    private UsersServiceClient usersServiceClient;

    @BeforeEach
    void prepararCliente() {
        repository.deleteAll();
        auditRepository.deleteAll();
        when(usersServiceClient.consultarEstado("DNI", "12345678"))
                .thenReturn(new ClienteEstadoResponse(EstadoCliente.ACTIVE));
    }

    @Test
    @DisplayName("Emite un JWT HS256 verificable y reemplaza la credencial anterior con auditoría atómica")
    void emiteYReemplazaCredencial() throws Exception {
        var primera = emitir();
        var segunda = emitir();
        var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(
                "clave-exclusiva-de-tests-de-al-menos-32-bytes".getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        var jwt = decoder.decode(segunda.get("credencial").asText());

        assertThat(jwt.getSubject()).isEqualTo(NUMERO);
        assertThat(jwt.getClaimAsString("doc")).isEqualTo("DNI:12345678");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getIssuedAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(jwt.getExpiresAt()).isEqualTo(Instant.parse(segunda.get("fechaExpiracion").asText()));
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(1440 * 60));
        assertThat(primera.get("credencial")).isNotEqualTo(segunda.get("credencial"));
        assertThat(repository.findAll()).hasSize(2);
        assertThat(repository.findByNumeroWhatsappAndEstado(NUMERO, CredencialEstado.ACTIVA)).isPresent();
        assertThat(repository.findAll().stream().filter(c -> c.getEstado() == CredencialEstado.REVOCADA))
                .singleElement().satisfies(c -> {
                    assertThat(c.getMotivoRevocacion()).isEqualTo(MotivoRevocacion.REEMPLAZO);
                    assertThat(c.getFechaRevocacion()).isNotNull();
                });
        assertThat(auditRepository.findAll()).hasSize(3).allSatisfy(a -> {
            assertThat(a.getResultado()).isEqualTo(AuditResultado.EXITOSO);
            assertThat(a.getJti()).isNotBlank();
            assertThat(a.getMotivo()).doesNotContain(segunda.get("credencial").asText());
        });
    }

    @Test
    @DisplayName("Valida el JWT emitido y rechaza el anterior después de reemplazarlo")
    void validaCredencialEmitidaYRechazaReemplazada() throws Exception {
        var primera = emitir();
        String primeraRequest = objectMapper.writeValueAsString(
                new ValidarCredencialRequest(primera.get("credencial").asText()));

        mockMvc.perform(post("/api/v1/credenciales/validar")
                .contentType(MediaType.APPLICATION_JSON).content(primeraRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vigente").value(true))
                .andExpect(jsonPath("$.numeroWhatsapp").value(NUMERO))
                .andExpect(jsonPath("$.documentoTipo").value("DNI"))
                .andExpect(jsonPath("$.documentoNumero").value("12345678"))
                .andExpect(jsonPath("$.fechaExpiracion").value(primera.get("fechaExpiracion").asText()));

        var segunda = emitir();

        mockMvc.perform(post("/api/v1/credenciales/validar")
                .contentType(MediaType.APPLICATION_JSON).content(primeraRequest))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
        mockMvc.perform(post("/api/v1/credenciales/validar")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                        new ValidarCredencialRequest(segunda.get("credencial").asText()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vigente").value(true));
        assertThat(auditRepository.findAll().stream()
                .filter(evento -> evento.getAccion() == AuditAccion.VALIDACION_FALLIDA))
                .singleElement().satisfies(evento ->
                        assertThat(evento.getResultado()).isEqualTo(AuditResultado.RECHAZADO));
    }

    @Test
    @DisplayName("El JWT emitido deja de autorizar inmediatamente después de la revocación de H3")
    void rechazaJwtEmitidoTrasRevocacion() throws Exception {
        var emitida = emitir();
        String solicitud = objectMapper.writeValueAsString(
                new ValidarCredencialRequest(emitida.get("credencial").asText()));
        mockMvc.perform(post("/api/v1/credenciales/validar")
                .contentType(MediaType.APPLICATION_JSON).content(solicitud))
                .andExpect(status().isOk());
        var activa = repository.findByNumeroWhatsappAndEstado(NUMERO, CredencialEstado.ACTIVA).orElseThrow();

        mockMvc.perform(post("/api/v1/credenciales/{numero}/revocacion", NUMERO)
                .header("Authorization", "Bearer h3-test-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"SOLICITUD_CLIENTE\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/credenciales/validar")
                .contentType(MediaType.APPLICATION_JSON).content(solicitud))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist());
        assertThat(repository.findByNumeroWhatsappAndEstado(NUMERO, CredencialEstado.ACTIVA)).isEmpty();
        assertThat(auditRepository.findAll().stream()
                .filter(evento -> evento.getAccion() == AuditAccion.REVOCACION))
                .singleElement().satisfies(evento -> {
                    assertThat(evento.getResultado()).isEqualTo(AuditResultado.EXITOSO);
                    assertThat(evento.getJti()).isEqualTo(activa.getJti());
                    assertThat(evento.getMotivo()).isEqualTo("BLOQUEO");
                });
        assertThat(auditRepository.findAll().stream()
                .filter(evento -> evento.getAccion() == AuditAccion.VALIDACION_FALLIDA))
                .singleElement().satisfies(evento -> {
                    assertThat(evento.getResultado()).isEqualTo(AuditResultado.RECHAZADO);
                    assertThat(evento.getJti()).isEqualTo(activa.getJti());
                    assertThat(evento.getNumeroWhatsapp()).isEqualTo(NUMERO);
                });
    }

    @Test
    @DisplayName("Rechaza sin confirmación de identidad o vínculo y conserva auditoría de rechazo")
    void rechazaSinConfirmacion() throws Exception {
        rechazar(REQUEST.replace("\"identidadVerificada\":true", "\"identidadVerificada\":false"), 403, "FORBIDDEN");
        rechazar(REQUEST.replace("\"vinculoActivo\":true", "\"vinculoActivo\":false"), 403, "FORBIDDEN");
        assertThat(repository.count()).isZero();
        assertThat(auditRepository.findAll()).hasSize(2).allSatisfy(a -> {
            assertThat(a.getAccion()).isEqualTo(AuditAccion.EMISION);
            assertThat(a.getResultado()).isEqualTo(AuditResultado.RECHAZADO);
        });
    }

    @Test
    @DisplayName("Un cliente suspendido no obtiene credencial")
    void rechazaClienteInactivo() throws Exception {
        when(usersServiceClient.consultarEstado("DNI", "12345678"))
                .thenReturn(new ClienteEstadoResponse(EstadoCliente.SUSPENDED));
        rechazar(REQUEST, 403, "FORBIDDEN");
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("La ausencia del cliente en H1 devuelve 404 controlado")
    void rechazaClienteInexistente() throws Exception {
        when(usersServiceClient.consultarEstado("DNI", "12345678")).thenThrow(new ClienteNoEncontradoException());
        rechazar(REQUEST, 404, "NOT_FOUND");
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("Una falla de H1 no revoca la credencial previa y devuelve 503")
    void conservaAnteriorSiH1Falla() throws Exception {
        emitir();
        when(usersServiceClient.consultarEstado("DNI", "12345678"))
                .thenThrow(new UsersServiceUnavailableException());
        rechazar(REQUEST, 503, "SERVICE_UNAVAILABLE");
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findByNumeroWhatsappAndEstado(NUMERO, CredencialEstado.ACTIVA)).isPresent();
    }

    @Test
    @DisplayName("Valida formato E.164 y confirmaciones obligatorias con contrato 400")
    void validaRequest() throws Exception {
        rechazar(REQUEST.replace(NUMERO, "invalido"), 400, "VALIDATION_ERROR");
        rechazar(REQUEST.replace("\"vinculoActivo\":true", "\"vinculoActivo\":null"), 400, "VALIDATION_ERROR");
    }

    private com.fasterxml.jackson.databind.JsonNode emitir() throws Exception {
        var response = mockMvc.perform(post("/api/v1/credenciales/emitir")
                .header("Authorization", "Bearer h3-test-token")
                .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.credencial").isString())
                .andReturn().getResponse();
        return objectMapper.readTree(response.getContentAsString());
    }

    private void rechazar(String request, int statusCode, String code) throws Exception {
        mockMvc.perform(post("/api/v1/credenciales/emitir")
                .header("Authorization", "Bearer h3-test-token").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().is(statusCode)).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.details").isArray()).andExpect(jsonPath("$.timestamp").exists());
    }
}
