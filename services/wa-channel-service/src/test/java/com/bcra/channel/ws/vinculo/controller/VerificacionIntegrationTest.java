package com.bcra.channel.ws.vinculo.controller;

import java.time.Instant;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bcra.channel.ws.vinculo.entity.AuditAccion;
import com.bcra.channel.ws.vinculo.entity.IntentoVerificacion;
import com.bcra.channel.ws.vinculo.entity.ResultadoIntento;
import com.bcra.channel.ws.vinculo.entity.Vinculo;
import com.bcra.channel.ws.vinculo.entity.VinculoEstado;
import com.bcra.channel.ws.vinculo.repository.IntentoVerificacionRepository;
import com.bcra.channel.ws.vinculo.repository.VinculoAuditLogRepository;
import com.bcra.channel.ws.vinculo.repository.VinculoRepository;
import com.bcra.channel.ws.vinculo.service.VerificadorIdentidad;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificacionIntegrationTest {

    private static final String NUMERO = "+5491112345678";
    private static final String RUTA = "/api/v1/vinculos/" + NUMERO + "/verificaciones";
    private static final String SOLICITUD = """
            {"documentoTipo":"DNI","documentoNumero":"12345678","datosVerificacion":{"prueba":"secreto"}}
            """;
    private static final WireMockServer H1 = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        H1.start();
    }

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("clients.wa-users-service.base-url", H1::baseUrl);
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:verificacion;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    }

    @MockitoBean
    private VerificadorIdentidad verificador;

    private final MockMvc mvc;
    private final VinculoRepository vinculos;
    private final IntentoVerificacionRepository intentos;
    private final VinculoAuditLogRepository auditoria;

    @Autowired
    VerificacionIntegrationTest(MockMvc mvc, VinculoRepository vinculos,
            IntentoVerificacionRepository intentos, VinculoAuditLogRepository auditoria) {
        this.mvc = mvc;
        this.vinculos = vinculos;
        this.intentos = intentos;
        this.auditoria = auditoria;
    }

    @BeforeEach
    void preparar() {
        intentos.deleteAll();
        vinculos.deleteAll();
        auditoria.deleteAll();
        H1.resetAll();
        estadoH1(200, "ACTIVE");
    }

    @AfterAll
    static void detenerH1() {
        H1.stop();
    }

    @Test
    @DisplayName("HTTP verifica contra H1 y persiste vínculo activo, intento y auditoría")
    void activaDesdeHttp() throws Exception {
        aceptarTerminos();
        when(verificador.esIdentidadValida("DNI", "12345678", Map.of("prueba", "secreto"))).thenReturn(true);

        verificar().andExpect(status().isOk())
                .andExpect(jsonPath("$.exitosa").value(true))
                .andExpect(jsonPath("$.vinculoId").isNotEmpty())
                .andExpect(jsonPath("$.estadoVinculo").value("ACTIVO"))
                .andExpect(jsonPath("$.verificadoEn").isNotEmpty());

        assertThat(vinculos.findByNumeroWhatsappAndEstado(NUMERO, VinculoEstado.ACTIVO)).hasSize(1);
        assertThat(intentos.findAll()).singleElement()
                .satisfies(intento -> assertThat(intento.getResultado()).isEqualTo(ResultadoIntento.EXITOSO));
        assertThat(auditoria.findAll()).extracting(evento -> evento.getAccion())
                .contains(AuditAccion.VERIFICACION_EXITOSA, AuditAccion.VINCULACION);
    }

    @Test
    @DisplayName("Tres fallos informan restantes y el siguiente intento devuelve 423")
    void limitaIntentosSinPerderFallos() throws Exception {
        aceptarTerminos();

        verificar().andExpect(status().isOk()).andExpect(jsonPath("$.intentosRestantes").value(2));
        verificar().andExpect(status().isOk()).andExpect(jsonPath("$.intentosRestantes").value(1));
        verificar().andExpect(status().isOk()).andExpect(jsonPath("$.intentosRestantes").value(0));
        verificar().andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("LOCKED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("soporte")))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        assertThat(intentos.count()).isEqualTo(3);
        assertThat(vinculos.findByNumeroWhatsappAndEstado(NUMERO, VinculoEstado.ACTIVO)).isEmpty();
    }

    @Test
    @DisplayName("Aceptar términos otra vez no reinicia la ventana de intentos")
    void noEvadeLimiteConNuevosTerminos() throws Exception {
        aceptarTerminos();
        verificar();
        verificar();
        verificar();
        aceptarTerminos();

        verificar().andExpect(status().isLocked());
        assertThat(intentos.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("La ventana vencida permite volver a verificar")
    void permiteTrasExpirarVentana() throws Exception {
        aceptarTerminos();
        Vinculo pendiente = vinculos.findAll().getFirst();
        for (int indice = 0; indice < 3; indice++) {
            IntentoVerificacion intento = new IntentoVerificacion();
            intento.setVinculoId(pendiente.getId());
            intento.setFecha(Instant.now().minusSeconds(3600));
            intento.setResultado(ResultadoIntento.FALLIDO);
            intentos.saveAndFlush(intento);
        }

        verificar().andExpect(status().isOk()).andExpect(jsonPath("$.intentosRestantes").value(2));
    }

    @Test
    @DisplayName("Sin términos devuelve 404 antes de identificar al cliente")
    void rechazaSinAceptacion() throws Exception {
        verificar().andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(intentos.count()).isZero();
    }

    @Test
    @DisplayName("H1 inexistente devuelve 404 y no registra intento")
    void traduce404DeH1() throws Exception {
        aceptarTerminos();
        estadoH1(404, "ACTIVE");

        verificar().andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(intentos.count()).isZero();
    }

    @Test
    @DisplayName("H1 suspendido devuelve conflicto")
    void rechazaH1Suspendido() throws Exception {
        aceptarTerminos();
        estadoH1(200, "SUSPENDED");

        verificar().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(intentos.count()).isZero();
    }

    @Test
    @DisplayName("Número activo para otro cliente devuelve conflicto auditado")
    void rechazaNumeroActivo() throws Exception {
        // ACTIVO de otro cliente + PENDIENTE del solicitante (vía repo: la API ya no crea pendiente si hay ACTIVO)
        Vinculo activo = new Vinculo();
        activo.setNumeroWhatsapp(NUMERO);
        activo.setEstado(VinculoEstado.ACTIVO);
        activo.setDocumentoTipo("DNI");
        activo.setDocumentoNumero("87654321");
        activo.setTerminosAceptados(true);
        activo.setFechaAceptacionTerminos(Instant.now());
        activo.setFechaActivacion(Instant.now());
        vinculos.saveAndFlush(activo);

        Vinculo pendiente = new Vinculo();
        pendiente.setNumeroWhatsapp(NUMERO);
        pendiente.setEstado(VinculoEstado.PENDIENTE_VERIFICACION);
        pendiente.setTerminosAceptados(true);
        pendiente.setFechaAceptacionTerminos(Instant.now());
        vinculos.saveAndFlush(pendiente);

        verificar().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(intentos.count()).isZero();
        assertThat(auditoria.findAll()).anySatisfy(evento -> {
            assertThat(evento.getAccion()).isEqualTo(AuditAccion.VINCULACION);
            assertThat(evento.getResultado().name()).isEqualTo("RECHAZADO");
        });
    }

    @ParameterizedTest
    @EnumSource(value = VinculoEstado.class, names = {"BLOQUEADO", "DESVINCULADO"})
    @DisplayName("Re-vincula con nuevo vínculo e intento sin reutilizar el vínculo anterior")
    void revinculaTrasCierre(VinculoEstado estado) throws Exception {
        aceptarTerminos();
        Vinculo anterior = vinculos.findAll().getFirst();
        anterior.setEstado(estado);
        anterior.setDocumentoTipo("DNI");
        anterior.setDocumentoNumero("12345678");
        vinculos.saveAndFlush(anterior);
        aceptarTerminos();
        when(verificador.esIdentidadValida("DNI", "12345678", Map.of("prueba", "secreto"))).thenReturn(true);

        verificar().andExpect(status().isOk()).andExpect(jsonPath("$.exitosa").value(true));

        assertThat(intentos.findAll()).singleElement().satisfies(intento ->
                assertThat(intento.getVinculoId()).isNotEqualTo(anterior.getId()));
        assertThat(vinculos.findById(anterior.getId()).orElseThrow().getEstado()).isEqualTo(estado);
        assertThat(auditoria.findAll()).extracting(evento -> evento.getAccion()).contains(AuditAccion.REVINCULACION);
    }

    @Test
    @DisplayName("Valida la solicitud sin exponer los datos de verificación")
    void validaBody() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = VinculoEstado.class, names = {"BLOQUEADO", "DESVINCULADO"})
    @DisplayName("Una revinculación fallida empieza sin los fallos anteriores y no activa el canal")
    void revinculacionFallidaNoReutilizaIntentos(VinculoEstado estado) throws Exception {
        aceptarTerminos();
        Vinculo anterior = vinculos.findAll().getFirst();
        anterior.setEstado(estado);
        anterior.setDocumentoTipo("DNI");
        anterior.setDocumentoNumero("12345678");
        vinculos.saveAndFlush(anterior);
        for (int indice = 0; indice < 3; indice++) {
            IntentoVerificacion intento = new IntentoVerificacion();
            intento.setVinculoId(anterior.getId());
            intento.setFecha(Instant.now());
            intento.setResultado(ResultadoIntento.FALLIDO);
            intentos.saveAndFlush(intento);
        }
        aceptarTerminos();

        verificar().andExpect(status().isOk())
                .andExpect(jsonPath("$.exitosa").value(false))
                .andExpect(jsonPath("$.intentosRestantes").value(2))
                .andExpect(jsonPath("$.estadoVinculo").value("PENDIENTE_VERIFICACION"));

        assertThat(vinculos.findById(anterior.getId()).orElseThrow().getEstado()).isEqualTo(estado);
        assertThat(vinculos.findByNumeroWhatsappAndEstado(NUMERO, VinculoEstado.ACTIVO)).isEmpty();
        assertThat(intentos.findAll()).hasSize(4).filteredOn(intento ->
                !intento.getVinculoId().equals(anterior.getId())).singleElement()
                .satisfies(intento -> assertThat(intento.getResultado()).isEqualTo(ResultadoIntento.FALLIDO));
        assertThat(auditoria.findAll()).extracting(evento -> evento.getAccion())
                .contains(AuditAccion.VERIFICACION_FALLIDA).doesNotContain(AuditAccion.REVINCULACION);
    }

    private void aceptarTerminos() throws Exception {
        mvc.perform(post("/api/v1/vinculos/terminos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"numeroWhatsapp\":\"" + NUMERO + "\"}")).andExpect(status().isCreated());
    }

    private ResultActions verificar() throws Exception {
        return mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(SOLICITUD));
    }

    private void estadoH1(int status, String estado) {
        H1.stubFor(get(urlEqualTo("/api/v1/users/DNI/12345678/status")).willReturn(aResponse()
                .withStatus(status).withHeader("Content-Type", "application/json")
                .withBody("{\"estado\":\"" + estado + "\"}")));
    }
}
