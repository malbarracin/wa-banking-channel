package com.bcra.cards.ws.tarjeta.controller;

import com.bcra.cards.ws.tarjeta.client.CredencialesServiceClient;
import com.bcra.cards.ws.tarjeta.client.dto.ValidarCredencialResponse;
import com.bcra.cards.ws.tarjeta.entity.AuditoriaAccion;
import com.bcra.cards.ws.tarjeta.entity.EstadoTarjeta;
import com.bcra.cards.ws.tarjeta.entity.MarcaTarjeta;
import com.bcra.cards.ws.tarjeta.entity.Tarjeta;
import com.bcra.cards.ws.tarjeta.entity.TipoBloqueo;
import com.bcra.cards.ws.tarjeta.entity.TipoTarjeta;
import com.bcra.cards.ws.tarjeta.repository.AuditoriaTarjetaRepository;
import com.bcra.cards.ws.tarjeta.repository.BloqueoRepository;
import com.bcra.cards.ws.tarjeta.repository.TarjetaRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BloqueoControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TarjetaRepository tarjetaRepository;
    @Autowired
    private BloqueoRepository bloqueoRepository;
    @Autowired
    private AuditoriaTarjetaRepository auditoriaRepository;
    @MockitoBean
    private CredencialesServiceClient credencialesClient;

    private Tarjeta tarjeta;

    @BeforeEach
    void preparar() {
        bloqueoRepository.deleteAll();
        auditoriaRepository.deleteAll();
        tarjetaRepository.deleteAll();
        tarjeta = new Tarjeta();
        tarjeta.setNumeroTarjetaHash("hash-sintetico");
        tarjeta.setUltimosCuatro("1234");
        tarjeta.setMarca(MarcaTarjeta.VISA);
        tarjeta.setTipo(TipoTarjeta.CREDITO);
        tarjeta.setTitularDocumentoTipo("DNI");
        tarjeta.setTitularDocumentoNumero("12345678");
        tarjeta.setEstado(EstadoTarjeta.ACTIVA);
        tarjeta.setActiva(true);
        tarjeta = tarjetaRepository.saveAndFlush(tarjeta);
        when(credencialesClient.validar("sesion")).thenReturn(new ValidarCredencialResponse(
                true, "+5491100000000", "DNI", "12345678", Instant.now().plusSeconds(300)));
    }

    @Test
    @DisplayName("CONFIRMAR persiste estado, bloqueo y auditoría y explica cómo desbloquear")
    void bloqueaConConfirmacion() throws Exception {
        solicitar(tarjeta.getId(), "\"CONFIRMAR\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BLOQUEO_TEMPORAL"))
                .andExpect(jsonPath("$.mensaje", containsString("DESBLOQUEAR")));
        assertThat(tarjetaRepository.findById(tarjeta.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTarjeta.BLOQUEO_TEMPORAL);
        assertThat(bloqueoRepository.findAll()).singleElement().satisfies(bloqueo -> {
            assertThat(bloqueo.getTarjetaId()).isEqualTo(tarjeta.getId());
            assertThat(bloqueo.getTipo()).isEqualTo(TipoBloqueo.TEMPORAL);
            assertThat(bloqueo.getFechaBloqueo()).isNotNull();
            assertThat(bloqueo.getUsuarioEjecutor()).isEqualTo("+5491100000000");
        });
        assertThat(auditoriaRepository.findAll()).singleElement().satisfies(auditoria -> {
            assertThat(auditoria.getAccion()).isEqualTo(AuditoriaAccion.BLOQUEAR_TEMPORAL);
            assertThat(auditoria.getTarjetaId()).isEqualTo(tarjeta.getId());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"confirmar\"", "\" CONFIRMAR\""})
    @DisplayName("Una confirmación ausente o distinta devuelve 400 sin efectos")
    void rechazaConfirmacionInvalida(String confirmar) throws Exception {
        solicitar(tarjeta.getId(), confirmar).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isString());
        verificarSinCambios();
    }

    @Test
    @DisplayName("Un body sin confirmar devuelve 400 sin cambiar estado")
    void rechazaConfirmacionOmitida() throws Exception {
        mockMvc.perform(post(ruta()).header("X-Credencial-Sesion", "sesion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tarjetaId\":\"" + tarjeta.getId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray());
        verificarSinCambios();
    }

    @Test
    @DisplayName("No acepta identificadores distintos entre path y body")
    void rechazaIdentificadorDistinto() throws Exception {
        solicitar(UUID.randomUUID(), "\"CONFIRMAR\"").andExpect(status().isBadRequest());
        verificarSinCambios();
    }

    @Test
    @DisplayName("Una tarjeta ajena recibe 404 sin revelar su existencia")
    void rechazaTarjetaAjena() throws Exception {
        tarjeta.setTitularDocumentoNumero("87654321");
        tarjetaRepository.saveAndFlush(tarjeta);
        solicitar(tarjeta.getId(), "\"CONFIRMAR\"").andExpect(status().isNotFound());
        verificarSinCambios();
    }

    @Test
    @DisplayName("Una tarjeta bloqueada no admite un nuevo bloqueo temporal")
    void rechazaEstadoIncompatible() throws Exception {
        tarjeta.setEstado(EstadoTarjeta.BLOQUEO_DEFINITIVO);
        tarjetaRepository.saveAndFlush(tarjeta);
        solicitar(tarjeta.getId(), "\"CONFIRMAR\"").andExpect(status().isConflict());
        assertThat(bloqueoRepository.count()).isZero();
        assertThat(auditoriaRepository.count()).isZero();
    }

    @Test
    @DisplayName("Sin credencial devuelve 401 sin modificar la tarjeta")
    void rechazaSinCredencial() throws Exception {
        mockMvc.perform(post(ruta()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tarjetaId\":\"" + tarjeta.getId() + "\",\"confirmar\":\"CONFIRMAR\"}"))
                .andExpect(status().isUnauthorized());
        verificarSinCambios();
    }

    @Test
    @DisplayName("Repetir un bloqueo temporal confirmado no duplica el bloqueo ni su auditoría")
    void rechazaBloqueoTemporalRepetido() throws Exception {
        solicitar(tarjeta.getId(), "\"CONFIRMAR\"").andExpect(status().isOk());
        var bloqueoOriginal = bloqueoRepository.findAll().getFirst();
        var auditoriaOriginal = auditoriaRepository.findAll().getFirst();

        solicitar(tarjeta.getId(), "\"CONFIRMAR\"")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details").isArray());

        assertThat(tarjetaRepository.findById(tarjeta.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTarjeta.BLOQUEO_TEMPORAL);
        assertThat(bloqueoRepository.findAll()).singleElement().satisfies(bloqueo -> {
            assertThat(bloqueo.getId()).isEqualTo(bloqueoOriginal.getId());
            assertThat(bloqueo.getFechaBloqueo()).isEqualTo(bloqueoOriginal.getFechaBloqueo());
        });
        assertThat(auditoriaRepository.findAll()).singleElement().satisfies(auditoria ->
                assertThat(auditoria.getId()).isEqualTo(auditoriaOriginal.getId()));
    }

    private org.springframework.test.web.servlet.ResultActions solicitar(UUID bodyId, String confirmar)
            throws Exception {
        return mockMvc.perform(post(ruta()).header("X-Credencial-Sesion", "sesion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tarjetaId\":\"" + bodyId + "\",\"confirmar\":" + confirmar + "}"));
    }

    private String ruta() {
        return "/api/v1/tarjetas/" + tarjeta.getId() + "/bloquear/temporal";
    }

    private void verificarSinCambios() {
        assertThat(tarjetaRepository.findById(tarjeta.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTarjeta.ACTIVA);
        assertThat(bloqueoRepository.count()).isZero();
        assertThat(auditoriaRepository.count()).isZero();
    }
}
