package com.bcra.channel.ws.vinculo.controller;

import java.time.Instant;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.channel.ws.vinculo.CredencialCanal;
import com.bcra.channel.ws.vinculo.entity.Preferencias;
import com.bcra.channel.ws.vinculo.entity.Vinculo;
import com.bcra.channel.ws.vinculo.entity.VinculoEstado;
import com.bcra.channel.ws.vinculo.repository.VinculoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EstadoPerfilIntegrationTest {

    private static final String NUMERO = "+5491199990018";
    private static final String RUTA_ESTADO = "/api/v1/vinculos/{numero}/estado";
    private static final String RUTA_PERFIL = "/api/v1/vinculos/{numero}/perfil";
    private static final WireMockServer H1 = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        H1.start();
    }

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("clients.wa-users-service.base-url", H1::baseUrl);
        registry.add("clients.credenciales-service.base-url", H1::baseUrl);
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:estado-perfil;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    }

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final VinculoRepository vinculoRepository;

    @Autowired
    EstadoPerfilIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper,
            VinculoRepository vinculoRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.vinculoRepository = vinculoRepository;
    }

    private void stubValidacion() {
        H1.stubFor(WireMock.post(WireMock.urlEqualTo("/api/v1/credenciales/validar"))
                .withRequestBody(WireMock.equalToJson("{\"credencial\":\"sesion-h2\"}"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"vigente\":true,\"numeroWhatsapp\":\"" + NUMERO
                                + "\",\"fechaExpiracion\":\"" + Instant.now().plusSeconds(300) + "\"}")));
    }

    @Test
    @DisplayName("El marcador booleano anterior no autoriza consultas de perfil")
    void rechazaHeaderControladoPorCliente() throws Exception {
        mockMvc.perform(get(RUTA_PERFIL, NUMERO).header("X-Canal-Credencial-Valida", "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        H1.verify(0, WireMock.postRequestedFor(WireMock.urlEqualTo("/api/v1/credenciales/validar")));
    }

    @BeforeEach
    void preparar() {
        vinculoRepository.deleteAll();
        H1.resetAll();
        stubValidacion();
    }

    @AfterAll
    static void detenerH1() {
        H1.stop();
    }

    @ParameterizedTest
    @EnumSource(VinculoEstado.class)
    @DisplayName("GET /estado devuelve 200 vinculado true y el estado sin datos del cliente")
    void consultaEstadoConVinculo(VinculoEstado estado) throws Exception {
        guardarVinculo(estado);

        String respuesta = mockMvc.perform(get(RUTA_ESTADO, NUMERO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroWhatsapp").value(NUMERO))
                .andExpect(jsonPath("$.vinculado").value(true))
                .andExpect(jsonPath("$.estado").value(estado.name()))
                .andExpect(jsonPath("$.documentoTipo").doesNotExist())
                .andExpect(jsonPath("$.documentoNumero").doesNotExist())
                .andExpect(jsonPath("$.nombre").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(respuesta).doesNotContain("DNI", "12345678", "Ana", "García");
    }

    @Test
    @DisplayName("GET /estado sin registro devuelve 200 vinculado false y nunca 404")
    void consultaEstadoSinVinculo() throws Exception {
        mockMvc.perform(get(RUTA_ESTADO, NUMERO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroWhatsapp").value(NUMERO))
                .andExpect(jsonPath("$.vinculado").value(false))
                .andExpect(jsonPath("$.estado").doesNotExist())
                .andExpect(jsonPath("$.documentoTipo").doesNotExist());
    }

    @Test
    @DisplayName("GET /perfil con credencial válida y vínculo ACTIVO combina H1 y preferencias locales")
    void consultaPerfilActivoConCredencial() throws Exception {
        Vinculo vinculo = guardarVinculo(VinculoEstado.ACTIVO);
        Preferencias preferencias = vinculo.getPreferencias();
        preferencias.setIdioma("es");
        preferencias.setNotificacionesHabilitadas(true);
        preferencias.setHorarioPreferido("09-18");
        vinculoRepository.saveAndFlush(vinculo);
        stubPerfilH1();

        mockMvc.perform(get(RUTA_PERFIL, NUMERO)
                        .header(CredencialCanal.HEADER_CREDENCIAL_TOKEN, "sesion-h2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.apellido").value("García"))
                .andExpect(jsonPath("$.nombreTratamiento").value("Sra."))
                .andExpect(jsonPath("$.email").value("ana@ejemplo.com"))
                .andExpect(jsonPath("$.telefono").value("+5491100000000"))
                .andExpect(jsonPath("$.preferencias.idioma").value("es"))
                .andExpect(jsonPath("$.preferencias.notificacionesHabilitadas").value(true))
                .andExpect(jsonPath("$.preferencias.horarioPreferido").value("09-18"))
                .andExpect(jsonPath("$.documentoNumero").doesNotExist())
                .andExpect(jsonPath("$.fechaNacimiento").doesNotExist());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"true", "false", "FALSE", "1", "si"})
    @DisplayName("GET /perfil sin contexto de credencial válida responde 401 UNAUTHORIZED")
    void rechazaPerfilSinCredencial(String header) throws Exception {
        guardarVinculo(VinculoEstado.ACTIVO);
        stubPerfilH1();

        MockHttpServletRequestBuilder solicitud = get(RUTA_PERFIL, NUMERO);
        if (header != null) {
            solicitud.header(CredencialCanal.HEADER_CREDENCIAL_TOKEN, header);
        }

        String respuesta = mockMvc.perform(solicitud)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.nombre").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(Instant.parse(objectMapper.readTree(respuesta).get("timestamp").asText())).isNotNull();
        assertThat(respuesta).doesNotContain("Ana", "García", "12345678");
        assertThat(H1.findAll(WireMock.getRequestedFor(urlEqualTo("/api/v1/users/DNI/12345678")))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = VinculoEstado.class, names = "ACTIVO", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("GET /perfil con vínculo no ACTIVO responde 409 CONFLICT")
    void rechazaPerfilConVinculoNoActivo(VinculoEstado estado) throws Exception {
        guardarVinculo(estado);
        stubPerfilH1();

        String respuesta = mockMvc.perform(get(RUTA_PERFIL, NUMERO)
                        .header(CredencialCanal.HEADER_CREDENCIAL_TOKEN, "sesion-h2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(respuesta).doesNotContain("Ana", "García");
        assertThat(H1.findAll(WireMock.getRequestedFor(urlEqualTo("/api/v1/users/DNI/12345678")))).isEmpty();
    }

    @Test
    @DisplayName("GET /perfil sin vínculo responde 409 CONFLICT con el contrato de error")
    void rechazaPerfilSinVinculo() throws Exception {
        mockMvc.perform(get(RUTA_PERFIL, NUMERO)
                        .header(CredencialCanal.HEADER_CREDENCIAL_TOKEN, "sesion-h2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    private Vinculo guardarVinculo(VinculoEstado estado) {
        Vinculo vinculo = new Vinculo();
        vinculo.setNumeroWhatsapp(NUMERO);
        vinculo.setDocumentoTipo("DNI");
        vinculo.setDocumentoNumero("12345678");
        vinculo.setEstado(estado);
        vinculo.setFechaUltimoCambioEstado(Instant.parse("2026-01-02T10:00:00Z"));
        return vinculoRepository.saveAndFlush(vinculo);
    }

    private void stubPerfilH1() {
        H1.stubFor(WireMock.get(urlEqualTo("/api/v1/users/DNI/12345678"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "nombre":"Ana",
                                  "apellido":"García",
                                  "nombreTratamiento":"Sra.",
                                  "email":"ana@ejemplo.com",
                                  "telefono":"+5491100000000",
                                  "documentoNumero":"12345678",
                                  "fechaNacimiento":"1990-01-01"
                                }
                                """)));
    }
}
