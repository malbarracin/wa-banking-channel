package com.bcra.channel.ws.vinculo.controller;

import java.time.Instant;
import java.util.List;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.channel.ws.vinculo.CredencialCanal;
import com.bcra.channel.ws.vinculo.entity.AuditAccion;
import com.bcra.channel.ws.vinculo.entity.AuditResultado;
import com.bcra.channel.ws.vinculo.entity.MotivoBloqueo;
import com.bcra.channel.ws.vinculo.entity.Vinculo;
import com.bcra.channel.ws.vinculo.entity.VinculoAuditLog;
import com.bcra.channel.ws.vinculo.entity.VinculoEstado;
import com.bcra.channel.ws.vinculo.repository.VinculoAuditLogRepository;
import com.bcra.channel.ws.vinculo.repository.VinculoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BloqueoDesvinculacionIntegrationTest {

    private static final String NUMERO = "+5491199990020";
    private static final String RUTA_BLOQUEO = "/api/v1/vinculos/{numero}/bloqueo";
    private static final String RUTA_DESVINCULACION = "/api/v1/vinculos/{numero}/desvinculacion";
    private static final String PATH_REVOCACION_REGEX = "/api/v1/credenciales/.*5491199990020/revocacion";
    private static final WireMockServer H2 = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        H2.start();
    }

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("clients.credenciales-service.base-url", H2::baseUrl);
        registry.add("clients.read-timeout", () -> "2s");
        registry.add("clients.connect-timeout", () -> "2s");
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:bloqueo-desv;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    }

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final VinculoRepository vinculoRepository;
    private final VinculoAuditLogRepository auditLogRepository;

    @Autowired
    BloqueoDesvinculacionIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper,
            VinculoRepository vinculoRepository, VinculoAuditLogRepository auditLogRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.vinculoRepository = vinculoRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @BeforeEach
    void preparar() {
        auditLogRepository.deleteAll();
        vinculoRepository.deleteAll();
        H2.resetAll();
        stubRevocacionExitosa();
    }

    @AfterAll
    static void detenerH2() {
        H2.stop();
    }

    @Test
    @DisplayName("PATCH /bloqueo SOLICITUD_CLIENTE con credencial → 200 BLOQUEADO y revocación H2")
    void bloqueoPorCliente() throws Exception {
        guardarVinculo(VinculoEstado.ACTIVO);

        mockMvc.perform(patch(RUTA_BLOQUEO, NUMERO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(CredencialCanal.HEADER_CREDENCIAL_VALIDA, "true")
                        .content("""
                                {"motivo":"SOLICITUD_CLIENTE","confirmacion":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroWhatsapp").value(NUMERO))
                .andExpect(jsonPath("$.estado").value("BLOQUEADO"));

        assertThat(vinculoRepository.findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(NUMERO))
                .hasValueSatisfying(v -> {
                    assertThat(v.getEstado()).isEqualTo(VinculoEstado.BLOQUEADO);
                    assertThat(v.getMotivoBloqueo()).isEqualTo(MotivoBloqueo.SOLICITUD_CLIENTE);
                });
        assertThat(auditoria(AuditAccion.BLOQUEO, AuditResultado.EXITOSO)).isPresent();
        H2.verify(postRequestedFor(urlMatching(PATH_REVOCACION_REGEX))
                .withRequestBody(equalToJson("{\"motivo\":\"SOLICITUD_CLIENTE\"}")));
    }

    @Test
    @DisplayName("PATCH /bloqueo POLITICA_BANCO sin credencial → 200 BLOQUEADO y revocación H2")
    void bloqueoPorPolitica() throws Exception {
        guardarVinculo(VinculoEstado.ACTIVO);

        mockMvc.perform(patch(RUTA_BLOQUEO, NUMERO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"motivo":"POLITICA_BANCO","confirmacion":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BLOQUEADO"));

        H2.verify(postRequestedFor(urlMatching(PATH_REVOCACION_REGEX))
                .withRequestBody(containing("POLITICA_BANCO")));
        assertThat(auditoria(AuditAccion.BLOQUEO, AuditResultado.EXITOSO)).isPresent();
    }

    @Test
    @DisplayName("PATCH /bloqueo sin confirmación → 400 VALIDATION_ERROR")
    void bloqueoSinConfirmacion() throws Exception {
        guardarVinculo(VinculoEstado.ACTIVO);

        String respuesta = mockMvc.perform(patch(RUTA_BLOQUEO, NUMERO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(CredencialCanal.HEADER_CREDENCIAL_VALIDA, "true")
                        .content("""
                                {"motivo":"SOLICITUD_CLIENTE","confirmacion":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(Instant.parse(objectMapper.readTree(respuesta).get("timestamp").asText())).isNotNull();
        assertThat(H2.findAll(postRequestedFor(urlMatching(PATH_REVOCACION_REGEX)))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"false", "1"})
    @DisplayName("PATCH /bloqueo SOLICITUD_CLIENTE sin credencial → 401 UNAUTHORIZED")
    void bloqueoSinCredencial(String header) throws Exception {
        guardarVinculo(VinculoEstado.ACTIVO);

        MockHttpServletRequestBuilder solicitud = patch(RUTA_BLOQUEO, NUMERO)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"motivo":"SOLICITUD_CLIENTE","confirmacion":true}
                        """);
        if (header != null) {
            solicitud.header(CredencialCanal.HEADER_CREDENCIAL_VALIDA, header);
        }

        mockMvc.perform(solicitud)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
        assertThat(H2.findAll(postRequestedFor(urlMatching(PATH_REVOCACION_REGEX)))).isEmpty();
    }

    @Test
    @DisplayName("PATCH /bloqueo de número inexistente → 404 NOT_FOUND")
    void bloqueoSinVinculo() throws Exception {
        mockMvc.perform(patch(RUTA_BLOQUEO, NUMERO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"motivo":"POLITICA_BANCO","confirmacion":true}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = VinculoEstado.class, names = {"BLOQUEADO", "DESVINCULADO"})
    @DisplayName("PATCH /bloqueo ya BLOQUEADO/DESVINCULADO → 409 CONFLICT")
    void bloqueoYaFinalizado(VinculoEstado estado) throws Exception {
        guardarVinculo(estado);

        mockMvc.perform(patch(RUTA_BLOQUEO, NUMERO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"motivo":"POLITICA_BANCO","confirmacion":true}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("PATCH /desvinculacion con confirmación y credencial → 200 DESVINCULADO")
    void desvinculacionExitosa() throws Exception {
        guardarVinculo(VinculoEstado.ACTIVO);

        mockMvc.perform(patch(RUTA_DESVINCULACION, NUMERO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(CredencialCanal.HEADER_CREDENCIAL_VALIDA, "true")
                        .content("""
                                {"confirmacion":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DESVINCULADO"));

        assertThat(auditoria(AuditAccion.DESVINCULACION, AuditResultado.EXITOSO)).isPresent();
        assertThat(auditoria(AuditAccion.DESVINCULACION, AuditResultado.RECHAZADO)).isEmpty();
        H2.verify(postRequestedFor(urlMatching(PATH_REVOCACION_REGEX)));
    }

    @Test
    @DisplayName("Si H2 falla el bloqueo local se aplica y se audita el fallo de revocación")
    void bloqueoConFalloH2() throws Exception {
        guardarVinculo(VinculoEstado.ACTIVO);
        H2.resetAll();
        H2.stubFor(WireMock.post(urlMatching(PATH_REVOCACION_REGEX))
                .willReturn(aResponse().withStatus(503).withBody("h2-caido")));

        mockMvc.perform(patch(RUTA_BLOQUEO, NUMERO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"motivo":"POLITICA_BANCO","confirmacion":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BLOQUEADO"));

        assertThat(vinculoRepository.findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(NUMERO))
                .hasValueSatisfying(v -> assertThat(v.getEstado()).isEqualTo(VinculoEstado.BLOQUEADO));
        assertThat(auditoria(AuditAccion.BLOQUEO, AuditResultado.EXITOSO)).isPresent();
        assertThat(auditoria(AuditAccion.BLOQUEO, AuditResultado.RECHAZADO)).isPresent();
        H2.verify(postRequestedFor(urlMatching(PATH_REVOCACION_REGEX)));
    }

    @Test
    @DisplayName("Si H2 da timeout la desvinculación local se aplica y se audita el fallo")
    void desvinculacionConTimeoutH2() throws Exception {
        guardarVinculo(VinculoEstado.BLOQUEADO);
        H2.resetAll();
        H2.stubFor(WireMock.post(urlMatching(PATH_REVOCACION_REGEX))
                .willReturn(aResponse().withFixedDelay(3500).withStatus(200)));

        mockMvc.perform(patch(RUTA_DESVINCULACION, NUMERO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(CredencialCanal.HEADER_CREDENCIAL_VALIDA, "true")
                        .content("""
                                {"confirmacion":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DESVINCULADO"));

        assertThat(auditoria(AuditAccion.DESVINCULACION, AuditResultado.EXITOSO)).isPresent();
        assertThat(auditoria(AuditAccion.DESVINCULACION, AuditResultado.RECHAZADO)).isPresent();
    }

    private Vinculo guardarVinculo(VinculoEstado estado) {
        Vinculo vinculo = new Vinculo();
        vinculo.setNumeroWhatsapp(NUMERO);
        vinculo.setDocumentoTipo("DNI");
        vinculo.setDocumentoNumero("22334455");
        vinculo.setEstado(estado);
        vinculo.setFechaUltimoCambioEstado(Instant.parse("2026-01-02T10:00:00Z"));
        return vinculoRepository.saveAndFlush(vinculo);
    }

    private void stubRevocacionExitosa() {
        H2.stubFor(WireMock.post(urlMatching(PATH_REVOCACION_REGEX))
                .willReturn(aResponse().withStatus(204)));
    }

    private java.util.Optional<VinculoAuditLog> auditoria(AuditAccion accion, AuditResultado resultado) {
        List<VinculoAuditLog> eventos = auditLogRepository.findByNumeroWhatsappOrderByFechaDesc(NUMERO);
        return eventos.stream()
                .filter(e -> e.getAccion() == accion && e.getResultado() == resultado)
                .findFirst();
    }
}
