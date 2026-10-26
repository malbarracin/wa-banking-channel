package com.bcra.channel.ws.vinculo.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import com.bcra.channel.ws.vinculo.entity.AuditAccion;
import com.bcra.channel.ws.vinculo.entity.AuditResultado;
import com.bcra.channel.ws.vinculo.entity.Vinculo;
import com.bcra.channel.ws.vinculo.entity.VinculoEstado;
import com.bcra.channel.ws.vinculo.repository.VinculoAuditLogRepository;
import com.bcra.channel.ws.vinculo.repository.VinculoRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AceptarTerminosIntegrationTest {

    private static final String RUTA = "/api/v1/vinculos/terminos";
    private static final String NUMERO = "+5491112345678";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final VinculoRepository vinculoRepository;
    private final VinculoAuditLogRepository auditRepository;

    @Autowired
    AceptarTerminosIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper,
            VinculoRepository vinculoRepository, VinculoAuditLogRepository auditRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.vinculoRepository = vinculoRepository;
        this.auditRepository = auditRepository;
    }

    @BeforeEach
    void limpiarRegistros() {
        vinculoRepository.deleteAll();
        auditRepository.deleteAll();
    }

    @Test
    @DisplayName("Acepta términos sin identificación y persiste el vínculo pendiente con su auditoría")
    void creaVinculoPendienteSinSolicitarIdentificacion() throws Exception {
        // La columna temporal conserva microsegundos; el reloj puede entregar nanosegundos.
        Instant inicio = Instant.now().truncatedTo(ChronoUnit.MICROS);

        String respuesta = mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroWhatsapp\":\"" + NUMERO + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.numeroWhatsapp").value(NUMERO))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_VERIFICACION"))
                .andExpect(jsonPath("$.fechaActivacion").isEmpty())
                .andExpect(jsonPath("$.documentoNumero").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode cuerpo = objectMapper.readTree(respuesta);
        Vinculo vinculo = vinculoRepository.findById(UUID.fromString(cuerpo.get("id").asText())).orElseThrow();
        assertThat(vinculo.getNumeroWhatsapp()).isEqualTo(NUMERO);
        assertThat(vinculo.getEstado()).isEqualTo(VinculoEstado.PENDIENTE_VERIFICACION);
        assertThat(vinculo.isTerminosAceptados()).isTrue();
        assertThat(vinculo.getFechaAceptacionTerminos()).isBetween(inicio, Instant.now());
        assertThat(vinculo.getDocumentoTipo()).isNull();
        assertThat(vinculo.getDocumentoNumero()).isNull();
        assertThat(vinculo.getFechaActivacion()).isNull();
        assertThat(auditRepository.findByNumeroWhatsappOrderByFechaDesc(NUMERO)).singleElement().satisfies(evento -> {
            assertThat(evento.getAccion()).isEqualTo(AuditAccion.ACEPTACION_TERMINOS);
            assertThat(evento.getResultado()).isEqualTo(AuditResultado.EXITOSO);
            assertThat(evento.getDocumentoTipo()).isNull();
            assertThat(evento.getDocumentoNumero()).isNull();
            assertThat(evento.getMotivo()).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"+14155552671", "+123456789012345"})
    @DisplayName("Acepta números internacionales y el límite de quince dígitos sin identificación")
    void aceptaNumerosInternacionalesValidos(String numeroWhatsapp) throws Exception {
        String solicitud = objectMapper.createObjectNode().put("numeroWhatsapp", numeroWhatsapp).toString();

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(solicitud))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroWhatsapp").value(numeroWhatsapp))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_VERIFICACION"));

        assertThat(vinculoRepository.count()).isEqualTo(1);
        assertThat(auditRepository.findByNumeroWhatsappOrderByFechaDesc(numeroWhatsapp)).hasSize(1);
    }

    @Test
    @DisplayName("Reutiliza el vínculo pendiente si el número vuelve a aceptar términos")
    void reutilizaVinculoPendienteExistente() throws Exception {
        String primera = mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroWhatsapp\":\"" + NUMERO + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String idOriginal = objectMapper.readTree(primera).get("id").asText();

        String segunda = mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroWhatsapp\":\"" + NUMERO + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(idOriginal))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_VERIFICACION"))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(segunda).get("id").asText()).isEqualTo(idOriginal);
        assertThat(vinculoRepository.count()).isEqualTo(1);
        assertThat(auditRepository.findByNumeroWhatsappOrderByFechaDesc(NUMERO)).hasSize(2)
                .allSatisfy(evento -> {
                    assertThat(evento.getAccion()).isEqualTo(AuditAccion.ACEPTACION_TERMINOS);
                    assertThat(evento.getResultado()).isEqualTo(AuditResultado.EXITOSO);
                });
    }

    @Test
    @DisplayName("Rechaza aceptar términos si el número ya tiene vínculo activo")
    void rechazaCuandoNumeroYaEstaActivo() throws Exception {
        Vinculo activo = new Vinculo();
        activo.setNumeroWhatsapp(NUMERO);
        activo.setEstado(VinculoEstado.ACTIVO);
        activo.setTerminosAceptados(true);
        activo.setFechaAceptacionTerminos(Instant.now());
        activo.setFechaActivacion(Instant.now());
        vinculoRepository.saveAndFlush(activo);

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroWhatsapp\":\"" + NUMERO + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertThat(vinculoRepository.count()).isEqualTo(1);
        assertThat(auditRepository.findByNumeroWhatsappOrderByFechaDesc(NUMERO)).singleElement().satisfies(evento -> {
            assertThat(evento.getAccion()).isEqualTo(AuditAccion.ACEPTACION_TERMINOS);
            assertThat(evento.getResultado()).isEqualTo(AuditResultado.RECHAZADO);
        });
    }

    @Test
    @DisplayName("Permite nuevo pendiente tras desvinculación para re-vinculación")
    void permiteNuevoPendienteTrasDesvinculacion() throws Exception {
        Vinculo desvinculado = new Vinculo();
        desvinculado.setNumeroWhatsapp(NUMERO);
        desvinculado.setEstado(VinculoEstado.DESVINCULADO);
        desvinculado.setTerminosAceptados(true);
        desvinculado.setFechaAceptacionTerminos(Instant.now());
        vinculoRepository.saveAndFlush(desvinculado);

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroWhatsapp\":\"" + NUMERO + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_VERIFICACION"))
                .andExpect(jsonPath("$.numeroWhatsapp").value(NUMERO));

        assertThat(vinculoRepository.count()).isEqualTo(2);
        assertThat(vinculoRepository.findByNumeroWhatsappAndEstado(NUMERO, VinculoEstado.PENDIENTE_VERIFICACION))
                .hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{", "{\"numeroWhatsapp\":{}}"})
    @DisplayName("Rechaza cuerpos ausentes o ilegibles con BAD_REQUEST y sin escrituras")
    void rechazaCuerpoIlegibleSinPersistir(String solicitud) throws Exception {
        String respuesta = mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(solicitud))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(Instant.parse(objectMapper.readTree(respuesta).get("timestamp").asText())).isNotNull();
        assertThat(vinculoRepository.count()).isZero();
        assertThat(auditRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"numeroWhatsapp\":null}", "{\"numeroWhatsapp\":\"\"}",
            "{\"numeroWhatsapp\":\" \"}", "{\"numeroWhatsapp\":\"5491112345678\"}",
            "{\"numeroWhatsapp\":\"+0123456789\"}", "{\"numeroWhatsapp\":\"+1234567890123456\"}",
            "{\"numeroWhatsapp\":\"+54abc\"}"})
    @DisplayName("Rechaza números ausentes o inválidos con el contrato único y sin persistir")
    void rechazaNumeroInvalido(String solicitud) throws Exception {
        String respuesta = mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(solicitud))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("numeroWhatsapp"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(Instant.parse(objectMapper.readTree(respuesta).get("timestamp").asText())).isNotNull();
        assertThat(vinculoRepository.count()).isZero();
        assertThat(auditRepository.count()).isZero();
    }
}
