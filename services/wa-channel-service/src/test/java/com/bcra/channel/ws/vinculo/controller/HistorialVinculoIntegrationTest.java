package com.bcra.channel.ws.vinculo.controller;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.channel.ws.vinculo.entity.AuditAccion;
import com.bcra.channel.ws.vinculo.entity.AuditResultado;
import com.bcra.channel.ws.vinculo.entity.Vinculo;
import com.bcra.channel.ws.vinculo.entity.VinculoAuditLog;
import com.bcra.channel.ws.vinculo.entity.VinculoEstado;
import com.bcra.channel.ws.vinculo.repository.VinculoAuditLogRepository;
import com.bcra.channel.ws.vinculo.repository.VinculoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HistorialVinculoIntegrationTest {

    private static final String NUMERO = "+5491199990021";
    private static final String RUTA = "/api/v1/vinculos/{numero}/historial";
    private static final Instant FECHA_ANTIGUA = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant FECHA_RECIENTE = Instant.parse("2026-01-02T10:00:00Z");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final VinculoRepository vinculoRepository;
    private final VinculoAuditLogRepository auditRepository;

    @Autowired
    HistorialVinculoIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper,
            VinculoRepository vinculoRepository, VinculoAuditLogRepository auditRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.vinculoRepository = vinculoRepository;
        this.auditRepository = auditRepository;
    }

    @ParameterizedTest
    @EnumSource(VinculoEstado.class)
    @DisplayName("Devuelve el estado más reciente y los eventos descendentes en cualquier estado del vínculo")
    void consultaHistorialEnCualquierEstado(VinculoEstado estado) throws Exception {
        guardarVinculo(VinculoEstado.DESVINCULADO, FECHA_ANTIGUA);
        guardarVinculo(estado, FECHA_RECIENTE);
        guardarEvento(NUMERO, AuditAccion.BLOQUEO, AuditResultado.RECHAZADO, FECHA_RECIENTE);
        guardarEvento(NUMERO, AuditAccion.ACEPTACION_TERMINOS, AuditResultado.EXITOSO, FECHA_ANTIGUA);
        guardarEvento("+5491199990022", AuditAccion.VINCULACION, AuditResultado.EXITOSO, FECHA_RECIENTE);

        mockMvc.perform(get(RUTA, NUMERO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroWhatsapp").value(NUMERO))
                .andExpect(jsonPath("$.documentoTipo").value("DNI"))
                .andExpect(jsonPath("$.documentoNumero").value("12345678"))
                .andExpect(jsonPath("$.estadoActual").value(estado.name()))
                .andExpect(jsonPath("$.eventos.length()").value(2))
                .andExpect(jsonPath("$.eventos[0].accion").value("BLOQUEO"))
                .andExpect(jsonPath("$.eventos[0].resultado").value("RECHAZADO"))
                .andExpect(jsonPath("$.eventos[0].fecha").value(FECHA_RECIENTE.toString()))
                .andExpect(jsonPath("$.eventos[1].accion").value("ACEPTACION_TERMINOS"))
                .andExpect(jsonPath("$.eventos[1].resultado").value("EXITOSO"))
                .andExpect(jsonPath("$.eventos[1].fecha").value(FECHA_ANTIGUA.toString()))
                .andExpect(jsonPath("$.eventos[0].motivo").doesNotExist())
                .andExpect(jsonPath("$.eventos[0].documentoNumero").doesNotExist());
    }

    @Test
    @DisplayName("Devuelve una lista vacía para un vínculo registrado sin eventos ni documento")
    void consultaVinculoSinEventos() throws Exception {
        Vinculo vinculo = guardarVinculo(VinculoEstado.PENDIENTE_VERIFICACION, FECHA_RECIENTE);
        vinculo.setDocumentoTipo(null);
        vinculo.setDocumentoNumero(null);
        vinculoRepository.saveAndFlush(vinculo);

        mockMvc.perform(get(RUTA, NUMERO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoActual").value("PENDIENTE_VERIFICACION"))
                .andExpect(jsonPath("$.eventos").isEmpty());
    }

    @Test
    @DisplayName("Devuelve NOT_FOUND con el contrato único cuando solo hay auditoría y ningún vínculo")
    void rechazaNumeroSinVinculoConContratoDeError() throws Exception {
        guardarEvento(NUMERO, AuditAccion.VERIFICACION_FALLIDA, AuditResultado.RECHAZADO, FECHA_RECIENTE);

        String respuesta = mockMvc.perform(get(RUTA, NUMERO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(Instant.parse(objectMapper.readTree(respuesta).get("timestamp").asText())).isNotNull();
        assertThat(respuesta).doesNotContain(NUMERO, "VinculoNotFoundException");
    }

    @Test
    @DisplayName("Selecciona estado y documento por fecha aunque el vínculo antiguo se inserte después")
    void seleccionaVinculoPorFechaDeEstado() throws Exception {
        guardarVinculo(VinculoEstado.ACTIVO, FECHA_RECIENTE);
        Vinculo antiguo = guardarVinculo(VinculoEstado.DESVINCULADO, FECHA_ANTIGUA);
        antiguo.setDocumentoTipo("PASAPORTE");
        antiguo.setDocumentoNumero("87654321");
        vinculoRepository.saveAndFlush(antiguo);

        mockMvc.perform(get(RUTA, NUMERO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroWhatsapp").value(NUMERO))
                .andExpect(jsonPath("$.estadoActual").value("ACTIVO"))
                .andExpect(jsonPath("$.documentoTipo").value("DNI"))
                .andExpect(jsonPath("$.documentoNumero").value("12345678"));
    }

    @Test
    @DisplayName("Devuelve NOT_FOUND cuando el número no tiene vínculos ni auditoría")
    void rechazaNumeroSinNingunRegistro() throws Exception {
        assertThat(vinculoRepository.findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(NUMERO))
                .isEmpty();
        assertThat(auditRepository.findByNumeroWhatsappOrderByFechaDesc(NUMERO)).isEmpty();

        mockMvc.perform(get(RUTA, NUMERO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    private Vinculo guardarVinculo(VinculoEstado estado, Instant fecha) {
        Vinculo vinculo = new Vinculo();
        vinculo.setNumeroWhatsapp(NUMERO);
        vinculo.setDocumentoTipo("DNI");
        vinculo.setDocumentoNumero("12345678");
        vinculo.setEstado(estado);
        vinculo.setFechaUltimoCambioEstado(fecha);
        return vinculoRepository.saveAndFlush(vinculo);
    }

    private void guardarEvento(String numero, AuditAccion accion, AuditResultado resultado, Instant fecha) {
        auditRepository.saveAndFlush(VinculoAuditLog.builder()
                .numeroWhatsapp(numero)
                .accion(accion)
                .resultado(resultado)
                .fecha(fecha)
                .motivo("Detalle omitido por seguridad")
                .build());
    }
}
