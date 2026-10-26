package com.bcra.credentials.ws.credencial.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bcra.credentials.ws.credencial.entity.AuditAccion;
import com.bcra.credentials.ws.credencial.entity.AuditResultado;
import com.bcra.credentials.ws.credencial.entity.CredencialEstado;
import com.bcra.credentials.ws.credencial.entity.CredencialSesion;
import com.bcra.credentials.ws.credencial.entity.MotivoRevocacion;
import com.bcra.credentials.ws.credencial.entity.RevocadaPor;
import com.bcra.credentials.ws.credencial.repository.CredencialAuditLogRepository;
import com.bcra.credentials.ws.credencial.repository.CredencialRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RevocacionContratoIntegrationTest {

    private static final String NUMERO = "+5491123456789";
    private static final String PATH = "/api/v1/credenciales/{numero}/revocacion";
    private static final long VIGENCIA_SEGUNDOS = 3600;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CredencialRepository repository;

    @Autowired
    private CredencialAuditLogRepository auditRepository;

    @BeforeEach
    void limpiar() {
        repository.deleteAll();
        auditRepository.deleteAll();
    }

    @ParameterizedTest
    @CsvSource({"DESVINCULACION, DESVINCULACION", "INTENTOS_AGOTADOS, BLOQUEO", "BLOQUEO, BLOQUEO"})
    @DisplayName("H3 revoca y conserva la identidad y el motivo normalizado en auditoría")
    void revocaDesdeH3(String motivoSolicitado, MotivoRevocacion motivoEsperado) throws Exception {
        var activa = guardar(CredencialEstado.ACTIVA);
        Instant inicio = Instant.now();

        mockMvc.perform(post(PATH, NUMERO).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"" + motivoSolicitado + "\",\"solicitadoPor\":null}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(repository.findById(activa.getId())).hasValueSatisfying(revocada -> {
            assertThat(revocada.getEstado()).isEqualTo(CredencialEstado.REVOCADA);
            assertThat(revocada.getFechaRevocacion()).isBetween(inicio, Instant.now());
            assertThat(revocada.getMotivoRevocacion()).isEqualTo(motivoEsperado);
            assertThat(revocada.getRevocadaPor()).isEqualTo(RevocadaPor.CANAL);
        });
        assertThat(auditRepository.findAll()).singleElement().satisfies(evento -> {
            assertThat(evento.getAccion()).isEqualTo(AuditAccion.REVOCACION);
            assertThat(evento.getResultado()).isEqualTo(AuditResultado.EXITOSO);
            assertThat(evento.getNumeroWhatsapp()).isEqualTo(NUMERO);
            assertThat(evento.getDocumentoTipo()).isEqualTo(activa.getDocumentoTipo());
            assertThat(evento.getDocumentoNumero()).isEqualTo(activa.getDocumentoNumero());
            assertThat(evento.getJti()).isEqualTo(activa.getJti());
            assertThat(evento.getMotivo()).isEqualTo(motivoEsperado.name());
            assertThat(evento.getFecha()).isBetween(inicio, Instant.now());
        });
    }

    @Test
    @DisplayName("Revoca solo la activa y conserva las credenciales históricas del mismo número")
    void conservaHistorialAlRevocarActiva() throws Exception {
        var anterior = guardar(CredencialEstado.REVOCADA);
        var expirada = guardar(CredencialEstado.EXPIRADA);
        var activa = guardar(CredencialEstado.ACTIVA);

        mockMvc.perform(post(PATH, NUMERO).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"DESVINCULACION\",\"solicitadoPor\":\"CANAL\"}"))
                .andExpect(status().isNoContent());

        assertThat(repository.findById(anterior.getId())).hasValueSatisfying(historica ->
                assertThat(historica).usingRecursiveComparison().isEqualTo(anterior));
        assertThat(repository.findById(expirada.getId())).hasValueSatisfying(historica ->
                assertThat(historica).usingRecursiveComparison().isEqualTo(expirada));
        assertThat(repository.findByNumeroWhatsappAndEstado(NUMERO, CredencialEstado.ACTIVA)).isEmpty();
        assertThat(auditRepository.findAll()).singleElement().satisfies(evento ->
                assertThat(evento.getJti()).isEqualTo(activa.getJti()));
    }

    @Test
    @DisplayName("Un número inexistente devuelve el contrato 404 y registra el rechazo")
    void rechazaNumeroInexistente() throws Exception {
        mockMvc.perform(post(PATH, NUMERO).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"FRAUDE\",\"solicitadoPor\":\"SISTEMA\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());

        assertThat(repository.findAll()).isEmpty();
        assertThat(auditRepository.findAll()).singleElement().satisfies(evento -> {
            assertThat(evento.getAccion()).isEqualTo(AuditAccion.REVOCACION);
            assertThat(evento.getResultado()).isEqualTo(AuditResultado.RECHAZADO);
            assertThat(evento.getNumeroWhatsapp()).isEqualTo(NUMERO);
            assertThat(evento.getMotivo()).isEqualTo("FRAUDE");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"motivo\":\"DESCONOCIDO\",\"solicitadoPor\":\"CANAL\"}",
        "{\"motivo\":\"FRAUDE\",\"solicitadoPor\":\"DESCONOCIDO\"}",
        "{"
    })
    @DisplayName("Rechaza JSON y enums inválidos sin modificar la credencial ni auditar éxito")
    void rechazaContenidoInvalido(String solicitud) throws Exception {
        var activa = guardar(CredencialEstado.ACTIVA);

        mockMvc.perform(post(PATH, NUMERO).contentType(MediaType.APPLICATION_JSON).content(solicitud))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertThat(repository.findById(activa.getId())).hasValueSatisfying(conservada ->
                assertThat(conservada).usingRecursiveComparison().isEqualTo(activa));
        assertThat(auditRepository.findAll()).isEmpty();
    }

    private CredencialSesion guardar(CredencialEstado estado) {
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var guardada = repository.saveAndFlush(CredencialSesion.builder()
                .jti(UUID.randomUUID().toString()).numeroWhatsapp(NUMERO)
                .documentoTipo("DNI").documentoNumero("12345678").vinculoId(UUID.randomUUID())
                .estado(estado).fechaEmision(ahora).fechaExpiracion(ahora.plusSeconds(VIGENCIA_SEGUNDOS))
                .build());
        return repository.findById(guardada.getId()).orElseThrow();
    }
}
