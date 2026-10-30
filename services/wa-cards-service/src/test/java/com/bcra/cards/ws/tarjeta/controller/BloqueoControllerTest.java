package com.bcra.cards.ws.tarjeta.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bcra.cards.ws.tarjeta.client.CredencialesServiceClient;
import com.bcra.cards.ws.tarjeta.client.dto.ValidarCredencialResponse;
import com.bcra.cards.ws.tarjeta.entity.AuditoriaAccion;
import com.bcra.cards.ws.tarjeta.entity.Bloqueo;
import com.bcra.cards.ws.tarjeta.entity.EstadoTarjeta;
import com.bcra.cards.ws.tarjeta.entity.MarcaTarjeta;
import com.bcra.cards.ws.tarjeta.entity.Tarjeta;
import com.bcra.cards.ws.tarjeta.entity.TipoBloqueo;
import com.bcra.cards.ws.tarjeta.entity.TipoTarjeta;
import com.bcra.cards.ws.tarjeta.repository.AuditoriaTarjetaRepository;
import com.bcra.cards.ws.tarjeta.repository.BloqueoRepository;
import com.bcra.cards.ws.tarjeta.repository.TarjetaRepository;
import com.bcra.cards.ws.tarjeta.service.BloqueoService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BloqueoControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private TarjetaRepository tarjetas;
    @Autowired
    private BloqueoRepository bloqueos;
    @Autowired
    private AuditoriaTarjetaRepository auditorias;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private BloqueoService servicio;
    @MockBean
    private CredencialesServiceClient credenciales;

    private Tarjeta tarjeta;

    @BeforeEach
    void preparar() {
        tarjeta = new Tarjeta();
        tarjeta.setNumeroTarjetaHash("hash-sintetico");
        tarjeta.setUltimosCuatro("1234");
        tarjeta.setMarca(MarcaTarjeta.VISA);
        tarjeta.setTipo(TipoTarjeta.CREDITO);
        tarjeta.setTitularDocumentoTipo("DNI");
        tarjeta.setTitularDocumentoNumero("10000001");
        tarjeta.setEstado(EstadoTarjeta.BLOQUEO_TEMPORAL);
        tarjeta.setActiva(false);
        tarjetas.saveAndFlush(tarjeta);
        when(credenciales.validar("sesion")).thenReturn(new ValidarCredencialResponse(true,
                "+5491100000001", "DNI", "10000001", Instant.now().plusSeconds(600)));
    }

    @Test
    @DisplayName("Reactiva la tarjeta, cierra el bloqueo temporal y persiste auditoría")
    void desbloqueaTemporal() throws Exception {
        Bloqueo bloqueo = new Bloqueo();
        bloqueo.setTarjetaId(tarjeta.getId());
        bloqueo.setTipo(TipoBloqueo.TEMPORAL);
        bloqueo.setFechaBloqueo(Instant.now().minusSeconds(60));
        bloqueo.setUsuarioEjecutor("sintetico");
        bloqueos.saveAndFlush(bloqueo);
        mvc.perform(post(ruta()).header("X-Credencial-Sesion", "sesion")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("true")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ACTIVA"));
        entityManager.flush();
        entityManager.clear();
        assertThat(tarjetas.findById(tarjeta.getId()).orElseThrow().isActiva()).isTrue();
        assertThat(bloqueos.findById(bloqueo.getId()).orElseThrow().getFechaDesbloqueo()).isNotNull();
        assertThat(auditorias.findAll()).anySatisfy(auditoria -> {
            assertThat(auditoria.getAccion()).isEqualTo(AuditoriaAccion.DESBLOQUEAR_TEMPORAL);
            assertThat(auditoria.getTarjetaId()).isEqualTo(tarjeta.getId());
        });
    }

    @Test
    @DisplayName("El bloqueo definitivo siempre devuelve 409 con derivación a canales formales")
    void rechazaDefinitivo() throws Exception {
        tarjeta.setEstado(EstadoTarjeta.BLOQUEO_DEFINITIVO);
        mvc.perform(post(ruta()).header("X-Credencial-Sesion", "sesion")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("true")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("canales formales")));
        assertThat(tarjeta.getEstado()).isEqualTo(EstadoTarjeta.BLOQUEO_DEFINITIVO);
        assertThat(auditorias.count()).isZero();
    }

    @Test
    @DisplayName("Requiere validación positiva y rechaza validación omitida o negativa")
    void exigeValidacion() throws Exception {
        for (String valor : new String[] {"false", "null"}) {
            mvc.perform(post(ruta()).header("X-Credencial-Sesion", "sesion")
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo(valor)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
        assertThat(tarjeta.getEstado()).isEqualTo(EstadoTarjeta.BLOQUEO_TEMPORAL);
        assertThat(auditorias.count()).isZero();
    }

    @Test
    @DisplayName("Permite omitir validación adicional cuando el banco desactiva el requisito")
    void permiteConfigurarValidacion() throws Exception {
        ReflectionTestUtils.setField(servicio, "validacionAdicionalRequerida", false);
        try {
            mvc.perform(post(ruta()).header("X-Credencial-Sesion", "sesion")
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo("null")))
                    .andExpect(status().isOk());
        } finally {
            ReflectionTestUtils.setField(servicio, "validacionAdicionalRequerida", true);
        }
    }

    @Test
    @DisplayName("Oculta tarjetas ajenas y exige credencial de sesión")
    void autorizaTitular() throws Exception {
        mvc.perform(post(ruta()).contentType(MediaType.APPLICATION_JSON).content(cuerpo("true")))
                .andExpect(status().isUnauthorized());
        tarjeta.setTitularDocumentoNumero("20000002");
        mvc.perform(post(ruta()).header("X-Credencial-Sesion", "sesion")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("true")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Rechaza identificadores inconsistentes y tarjetas sin bloqueo temporal")
    void rechazaSolicitudIncompatible() throws Exception {
        mvc.perform(post(ruta()).header("X-Credencial-Sesion", "sesion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("true").replace(tarjeta.getId().toString(), UUID.randomUUID().toString())))
                .andExpect(status().isBadRequest());
        tarjeta.setEstado(EstadoTarjeta.ACTIVA);
        mvc.perform(post(ruta()).header("X-Credencial-Sesion", "sesion")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("true")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("El definitivo es irreversible incluso con validación adicional desactivada")
    void rechazaDefinitivoSinValidacionRequerida() throws Exception {
        tarjeta.setEstado(EstadoTarjeta.BLOQUEO_DEFINITIVO);
        tarjetas.flush();
        ReflectionTestUtils.setField(servicio, "validacionAdicionalRequerida", false);
        try {
            mvc.perform(post(ruta()).header("X-Credencial-Sesion", "sesion")
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo("null")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.containsString("canales formales")));
            entityManager.clear();
            Tarjeta persistida = tarjetas.findById(tarjeta.getId()).orElseThrow();
            assertThat(persistida.getEstado()).isEqualTo(EstadoTarjeta.BLOQUEO_DEFINITIVO);
            assertThat(persistida.isActiva()).isFalse();
            assertThat(auditorias.count()).isZero();
        } finally {
            ReflectionTestUtils.setField(servicio, "validacionAdicionalRequerida", true);
        }
    }

    @Test
    @DisplayName("El desbloqueo conserva la fecha de los bloqueos históricos ya cerrados")
    void conservaHistorialCerrado() throws Exception {
        Instant cierreAnterior = Instant.parse("2026-01-02T00:00:00Z");
        Bloqueo historico = new Bloqueo();
        historico.setTarjetaId(tarjeta.getId());
        historico.setTipo(TipoBloqueo.TEMPORAL);
        historico.setFechaBloqueo(cierreAnterior.minusSeconds(60));
        historico.setFechaDesbloqueo(cierreAnterior);
        historico.setUsuarioEjecutor("sintetico");
        bloqueos.saveAndFlush(historico);

        mvc.perform(post(ruta()).header("X-Credencial-Sesion", "sesion")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"));
        entityManager.flush();
        entityManager.clear();
        assertThat(bloqueos.findById(historico.getId()).orElseThrow().getFechaDesbloqueo())
                .isEqualTo(cierreAnterior);
    }

    @Test
    @DisplayName("Bloquea definitivamente por robo o extravío y registra motivo y auditoría")
    void bloqueaDefinitivo() throws Exception {
        for (String motivo : new String[] {"ROBO", "EXTRAVIO"}) {
            tarjeta.setEstado(EstadoTarjeta.ACTIVA);
            tarjeta.setActiva(true);
            mvc.perform(post(rutaDefinitiva()).header("X-Credencial-Sesion", "sesion")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpoDefinitivo(motivo, "CONFIRMAR", "CONFIRMAR")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tarjeta.estado").value("BLOQUEO_DEFINITIVO"))
                    .andExpect(jsonPath("$.mensaje")
                            .value(org.hamcrest.Matchers.containsString("canales oficiales")));
            entityManager.flush();
            entityManager.clear();
            tarjeta = tarjetas.findById(tarjeta.getId()).orElseThrow();
            assertThat(tarjeta.isActiva()).isFalse();
            assertThat(bloqueos.findByTarjetaIdOrderByFechaBloqueoDesc(tarjeta.getId()))
                    .anySatisfy(bloqueo -> {
                        assertThat(bloqueo.getTipo()).isEqualTo(TipoBloqueo.DEFINITIVO);
                        assertThat(bloqueo.getMotivo()).isEqualTo(motivo);
                        assertThat(bloqueo.isDobleConfirmacionCompleta()).isTrue();
                    });
            assertThat(auditorias.findAll()).anySatisfy(auditoria -> {
                assertThat(auditoria.getAccion()).isEqualTo(AuditoriaAccion.BLOQUEAR_DEFINITIVO);
                assertThat(auditoria.getTarjetaId()).isEqualTo(tarjeta.getId());
            });
        }
    }

    @Test
    @DisplayName("Advierte irreversibilidad antes de solicitar confirmaciones y no modifica datos")
    void advierteSinConfirmaciones() throws Exception {
        for (String[] confirmaciones : new String[][] {
                {null, null}, {"CONFIRMAR", null}, {null, "CONFIRMAR"}, {"confirmar", "CONFIRMAR"}}) {
            mvc.perform(post(rutaDefinitiva()).header("X-Credencial-Sesion", "sesion")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpoDefinitivo("ROBO", confirmaciones[0], confirmaciones[1])))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"))
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.startsWith(
                                    "El bloqueo definitivo es irreversible por WhatsApp.")));
        }
        entityManager.flush();
        entityManager.clear();
        assertThat(tarjetas.findById(tarjeta.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTarjeta.BLOQUEO_TEMPORAL);
        assertThat(bloqueos.count()).isZero();
        assertThat(auditorias.count()).isZero();
    }

    @Test
    @DisplayName("Rechaza motivo inválido, tarjeta ajena, credencial ausente y repetición del bloqueo")
    void rechazaDefinitivoInvalido() throws Exception {
        String cuerpo = cuerpoDefinitivo("ROBO", "CONFIRMAR", "CONFIRMAR");
        mvc.perform(post(rutaDefinitiva()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(rutaDefinitiva()).header("X-Credencial-Sesion", "sesion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDefinitivo("OTRO", "CONFIRMAR", "CONFIRMAR")))
                .andExpect(status().isBadRequest());
        mvc.perform(post(rutaDefinitiva()).header("X-Credencial-Sesion", "sesion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo.replace(tarjeta.getId().toString(), UUID.randomUUID().toString())))
                .andExpect(status().isBadRequest());
        tarjeta.setTitularDocumentoNumero("20000002");
        mvc.perform(post(rutaDefinitiva()).header("X-Credencial-Sesion", "sesion")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isNotFound());
        tarjeta.setTitularDocumentoNumero("10000001");
        tarjeta.setEstado(EstadoTarjeta.BLOQUEO_DEFINITIVO);
        mvc.perform(post(rutaDefinitiva()).header("X-Credencial-Sesion", "sesion")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isConflict());
        assertThat(bloqueos.count()).isZero();
        assertThat(auditorias.count()).isZero();
    }

    private String rutaDefinitiva() {
        return "/api/v1/tarjetas/" + tarjeta.getId() + "/bloquear/definitivo";
    }

    private String cuerpoDefinitivo(String motivo, String primero, String segundo) {
        return "{\"tarjetaId\":\"" + tarjeta.getId() + "\",\"motivo\":\"" + motivo
                + "\",\"confirmarPrimero\":" + (primero == null ? "null" : "\"" + primero + "\"")
                + ",\"confirmarSegundo\":" + (segundo == null ? "null" : "\"" + segundo + "\"") + "}";
    }

    private String ruta() {
        return "/api/v1/tarjetas/" + tarjeta.getId() + "/desbloquear/temporal";
    }

    private String cuerpo(String validacion) {
        return "{\"tarjetaId\":\"" + tarjeta.getId() + "\",\"validacionAdicional\":" + validacion + "}";
    }
}
