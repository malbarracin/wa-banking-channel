package com.bcra.credentials.ws.credencial;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bcra.credentials.ws.credencial.entity.AuditAccion;
import com.bcra.credentials.ws.credencial.entity.AuditResultado;
import com.bcra.credentials.ws.credencial.entity.CredencialAuditLog;
import com.bcra.credentials.ws.credencial.entity.CredencialEstado;
import com.bcra.credentials.ws.credencial.entity.CredencialSesion;
import com.bcra.credentials.ws.credencial.repository.CredencialAuditLogRepository;
import com.bcra.credentials.ws.credencial.repository.CredencialRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HistorialCredencialIntegrationTest {

    private static final String NUMERO = "+5491111111111";
    private static final String ENDPOINT = "/api/v1/credenciales/{numero}/historial";
    private static final Instant FECHA = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CredencialRepository repository;
    @Autowired
    private CredencialAuditLogRepository auditRepository;

    @BeforeEach
    void limpiarRegistros() {
        auditRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    @DisplayName("Devuelve la última credencial y todos los eventos descendentes sin exponer su identificador")
    void consultaHistorialOrdenadoSinSecretos() throws Exception {
        guardarCredencial(CredencialEstado.REVOCADA, FECHA, FECHA.plusSeconds(60));
        guardarCredencial(CredencialEstado.ACTIVA, FECHA.plusSeconds(120), Instant.now().plusSeconds(3600));
        guardarEvento(NUMERO, AuditAccion.EMISION, FECHA);
        guardarEvento(NUMERO, AuditAccion.REVOCACION, FECHA.plusSeconds(120));
        guardarEvento(NUMERO, AuditAccion.RENOVACION, FECHA.plusSeconds(60));
        guardarEvento(NUMERO, AuditAccion.VALIDACION_FALLIDA, FECHA.plusSeconds(180));
        guardarEvento("+5491122222222", AuditAccion.EMISION, FECHA.plusSeconds(240));

        String respuesta = mockMvc.perform(get(ENDPOINT, NUMERO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroWhatsapp").value(NUMERO))
                .andExpect(jsonPath("$.documentoTipo").value("DNI"))
                .andExpect(jsonPath("$.documentoNumero").value("12345678"))
                .andExpect(jsonPath("$.estadoActual").value("ACTIVA"))
                .andExpect(jsonPath("$.eventos.length()").value(4))
                .andExpect(jsonPath("$.eventos[0].accion").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.eventos[0].resultado").value("RECHAZADO"))
                .andExpect(jsonPath("$.eventos[0].fecha").value("2026-01-01T00:03:00Z"))
                .andExpect(jsonPath("$.eventos[1].accion").value("REVOCACION"))
                .andExpect(jsonPath("$.eventos[2].accion").value("RENOVACION"))
                .andExpect(jsonPath("$.eventos[3].accion").value("EMISION"))
                .andReturn().getResponse().getContentAsString();

        assertThat(respuesta).doesNotContain("jti", "identificador-interno", "vinculoId", "secret", "token");
        assertThat(auditRepository.count()).isEqualTo(5);
    }

    @Test
    @DisplayName("Calcula EXPIRADA sin alterar la credencial ni generar auditoría")
    void informaExpiracionEfectiva() throws Exception {
        guardarCredencial(CredencialEstado.ACTIVA, FECHA, FECHA.plusSeconds(60));
        guardarEvento(NUMERO, AuditAccion.EMISION, FECHA);

        mockMvc.perform(get(ENDPOINT, NUMERO)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoActual").value("EXPIRADA"));

        assertThat(repository.findAll()).singleElement()
                .satisfies(c -> assertThat(c.getEstado()).isEqualTo(CredencialEstado.ACTIVA));
        assertThat(auditRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Conserva REVOCADA aunque su fecha de expiración haya pasado")
    void conservaEstadoRevocado() throws Exception {
        guardarCredencial(CredencialEstado.REVOCADA, FECHA, FECHA.plusSeconds(60));
        guardarEvento(NUMERO, AuditAccion.REVOCACION, FECHA);

        mockMvc.perform(get(ENDPOINT, NUMERO)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoActual").value("REVOCADA"));
    }

    @Test
    @DisplayName("Un número desconocido devuelve el contrato 404 sin detalles internos")
    void rechazaNumeroSinRegistros() throws Exception {
        verificarNoEncontrado();
    }

    @Test
    @DisplayName("Una credencial sin auditoría devuelve 404")
    void rechazaCredencialSinEventos() throws Exception {
        guardarCredencial(CredencialEstado.ACTIVA, FECHA, Instant.now().plusSeconds(3600));

        verificarNoEncontrado();
    }

    @Test
    @DisplayName("Los rechazos auditados sin credencial emitida devuelven 404")
    void rechazaEventosSinCredencial() throws Exception {
        guardarEvento(NUMERO, AuditAccion.VALIDACION_FALLIDA, FECHA);

        verificarNoEncontrado();
    }

    @Test
    @DisplayName("Valida el número E.164 con el contrato de error 400")
    void rechazaNumeroInvalido() throws Exception {
        mockMvc.perform(get(ENDPOINT, "numero-invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private void verificarNoEncontrado() throws Exception {
        mockMvc.perform(get(ENDPOINT, NUMERO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    private void guardarCredencial(CredencialEstado estado, Instant emision, Instant expiracion) {
        repository.saveAndFlush(CredencialSesion.builder().jti(UUID.randomUUID().toString())
                .numeroWhatsapp(NUMERO).documentoTipo("DNI").documentoNumero("12345678")
                .vinculoId(UUID.randomUUID()).estado(estado).fechaEmision(emision)
                .fechaExpiracion(expiracion).build());
    }

    private void guardarEvento(String numero, AuditAccion accion, Instant fecha) {
        auditRepository.saveAndFlush(CredencialAuditLog.builder().numeroWhatsapp(numero)
                .accion(accion).resultado(accion == AuditAccion.VALIDACION_FALLIDA
                        ? AuditResultado.RECHAZADO : AuditResultado.EXITOSO)
                .jti("identificador-interno").fecha(fecha).build());
    }
}
