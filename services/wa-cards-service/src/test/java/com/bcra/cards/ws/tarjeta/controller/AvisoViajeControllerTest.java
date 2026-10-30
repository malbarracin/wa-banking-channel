package com.bcra.cards.ws.tarjeta.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bcra.cards.ws.exception.ForbiddenException;
import com.bcra.cards.ws.exception.UnauthorizedException;
import com.bcra.cards.ws.tarjeta.client.CredencialesServiceClient;
import com.bcra.cards.ws.tarjeta.client.dto.ValidarCredencialResponse;
import com.bcra.cards.ws.tarjeta.entity.EstadoTarjeta;
import com.bcra.cards.ws.tarjeta.entity.MarcaTarjeta;
import com.bcra.cards.ws.tarjeta.entity.Tarjeta;
import com.bcra.cards.ws.tarjeta.entity.TipoTarjeta;
import com.bcra.cards.ws.tarjeta.repository.AvisoViajeRepository;
import com.bcra.cards.ws.tarjeta.repository.TarjetaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "cards.aviso-viaje.recomendaciones=Contactá al banco por canales oficiales.")
@AutoConfigureMockMvc
@Transactional
class AvisoViajeControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TarjetaRepository tarjetas;
    @Autowired
    private AvisoViajeRepository avisos;
    @Autowired
    private EntityManager entityManager;
    @MockBean
    private CredencialesServiceClient credenciales;

    private UUID tarjetaId;

    @BeforeEach
    void preparar() {
        var tarjeta = new Tarjeta();
        tarjeta.setNumeroTarjetaHash("hash-sintetico-viaje");
        tarjeta.setUltimosCuatro("1234");
        tarjeta.setMarca(MarcaTarjeta.VISA);
        tarjeta.setTipo(TipoTarjeta.CREDITO);
        tarjeta.setTitularDocumentoTipo("DNI");
        tarjeta.setTitularDocumentoNumero("10000001");
        tarjeta.setEstado(EstadoTarjeta.ACTIVA);
        tarjeta.setActiva(true);
        tarjetaId = tarjetas.saveAndFlush(tarjeta).getId();
        when(credenciales.validar("sesion")).thenReturn(new ValidarCredencialResponse(true,
                "+5491100000001", "DNI", "10000001", Instant.now().plusSeconds(600)));
    }

    @Test
    @DisplayName("Registra y confirma destino, fechas y recomendaciones sin alterar la tarjeta")
    void registraYConfirma() throws Exception {
        String respuesta = mvc.perform(post(ruta()).header("X-Credencial-Sesion", "sesion")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(tarjetaId, "\"Madrid\"",
                                "2027-01-10", "2027-01-20")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tarjetaId").value(tarjetaId.toString()))
                .andExpect(jsonPath("$.pais").value("España"))
                .andExpect(jsonPath("$.region").value("Madrid"))
                .andExpect(jsonPath("$.fechaDesde").value("2027-01-10"))
                .andExpect(jsonPath("$.fechaHasta").value("2027-01-20"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(respuesta).contains("confirmado", "España", "Madrid", "2027-01-10", "2027-01-20",
                "Contactá al banco por canales oficiales.", "políticas internacionales del emisor",
                "límites del banco").doesNotContain("PAN", "CVV", "vencimiento", "hash-sintetico", "1234");
        entityManager.flush();
        entityManager.clear();
        var aviso = avisos.findByTarjetaIdOrderByFechaRegistroDesc(tarjetaId).getFirst();
        assertThat(aviso.isConfirmado()).isTrue();
        assertThat(aviso.getFechaRegistro()).isNotNull();
        assertThat(aviso.getFechaDesde()).isEqualTo(LocalDate.of(2027, 1, 10));
        assertThat(aviso.getFechaHasta()).isEqualTo(LocalDate.of(2027, 1, 20));
        assertThat(aviso.getPais()).isEqualTo("España");
        assertThat(aviso.getRegion()).isEqualTo("Madrid");
        assertThat(tarjetas.findById(tarjetaId).orElseThrow().getEstado()).isEqualTo(EstadoTarjeta.ACTIVA);
    }

    @Test
    @DisplayName("Permite región omitida e intervalo de un solo día")
    void permiteRegionOpcional() throws Exception {
        mvc.perform(post(ruta()).header("X-Credencial-Sesion", "sesion")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(tarjetaId, "null",
                                "2027-01-10", "2027-01-10")))
                .andExpect(status().isCreated());
        assertThat(avisos.findByTarjetaIdOrderByFechaRegistroDesc(tarjetaId)).singleElement()
                .satisfies(aviso -> assertThat(aviso.getRegion()).isNull());
    }

    @Test
    @DisplayName("Sin credencial responde 401 y no registra avisos ni llama a H2")
    void exigeCredencial() throws Exception {
        mvc.perform(post(ruta()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(tarjetaId, "null", "2027-01-10", "2027-01-20")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(credenciales);
        assertThat(avisos.count()).isZero();
    }

    @Test
    @DisplayName("Credencial rechazada por H2 responde 401 sin registrar")
    void rechazaCredencialInvalida() throws Exception {
        when(credenciales.validar("sesion")).thenThrow(new UnauthorizedException("Credencial no válida"));
        mvc.perform(post(ruta()).header("X-Credencial-Sesion", "sesion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(tarjetaId, "null", "2027-01-10", "2027-01-20")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        assertThat(avisos.count()).isZero();
    }

    @Test
    @DisplayName("Tarjeta ajena e inexistente responden 404 sin registrar")
    void ocultaTarjetaAjena() throws Exception {
        when(credenciales.validar("sesion")).thenReturn(new ValidarCredencialResponse(true,
                "+5491100000002", "DNI", "20000002", Instant.now().plusSeconds(600)));
        for (UUID id : new UUID[] {tarjetaId, UUID.randomUUID()}) {
            mvc.perform(post("/api/v1/tarjetas/" + id + "/aviso-viaje")
                            .header("X-Credencial-Sesion", "sesion").contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(id, "null", "2027-01-10", "2027-01-20")))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
        assertThat(avisos.count()).isZero();
    }

    @Test
    @DisplayName("Rechaza intervalo invertido e identificador discordante sin registrar")
    void rechazaSolicitudInconsistente() throws Exception {
        for (String cuerpo : new String[] {
                cuerpo(tarjetaId, "null", "2027-01-20", "2027-01-10"),
                cuerpo(UUID.randomUUID(), "null", "2027-01-10", "2027-01-20")}) {
            mvc.perform(post(ruta()).header("X-Credencial-Sesion", "sesion")
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
        assertThat(avisos.count()).isZero();
    }

    @Test
    @DisplayName("Valida país y fechas obligatorios")
    void validaCampos() throws Exception {
        mvc.perform(post(ruta()).header("X-Credencial-Sesion", "sesion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tarjetaId\":\"" + tarjetaId + "\",\"pais\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(avisos.count()).isZero();
    }

    @Test
    @DisplayName("Registra un destino sin la propiedad región en el JSON")
    void permiteRegionOmitidaDelJson() throws Exception {
        ObjectNode solicitud = (ObjectNode) objectMapper.readTree(
                cuerpo(tarjetaId, "null", "2027-01-10", "2027-01-20"));
        solicitud.remove("region");
        mvc.perform(post(ruta()).header("X-Credencial-Sesion", "sesion")
                        .contentType(MediaType.APPLICATION_JSON).content(solicitud.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pais").value("España"))
                .andExpect(jsonPath("$.mensajeConfirmacion").isNotEmpty());
        entityManager.flush();
        entityManager.clear();
        assertThat(avisos.findByTarjetaIdOrderByFechaRegistroDesc(tarjetaId)).singleElement()
                .satisfies(aviso -> {
                    assertThat(aviso.getRegion()).isNull();
                    assertThat(aviso.isConfirmado()).isTrue();
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"tarjetaId", "pais", "fechaDesde", "fechaHasta"})
    @DisplayName("Cada campo obligatorio ausente devuelve su detalle y no persiste")
    void rechazaCampoObligatorioOmitido(String campo) throws Exception {
        ObjectNode solicitud = (ObjectNode) objectMapper.readTree(
                cuerpo(tarjetaId, "null", "2027-01-10", "2027-01-20"));
        solicitud.remove(campo);
        mvc.perform(post(ruta()).header("X-Credencial-Sesion", "sesion")
                        .contentType(MediaType.APPLICATION_JSON).content(solicitud.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details[0].field").value(campo))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
        verifyNoInteractions(credenciales);
        assertThat(avisos.count()).isZero();
    }

    @Test
    @DisplayName("Vínculo inactivo devuelve 403 con contrato de error y no persiste")
    void rechazaVinculoInactivo() throws Exception {
        when(credenciales.validar("sesion")).thenThrow(new ForbiddenException("Vínculo inactivo"));
        mvc.perform(post(ruta()).header("X-Credencial-Sesion", "sesion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(tarjetaId, "null", "2027-01-10", "2027-01-20")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Acceso no permitido"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
        assertThat(avisos.count()).isZero();
    }

    private String ruta() {
        return "/api/v1/tarjetas/" + tarjetaId + "/aviso-viaje";
    }

    private String cuerpo(UUID id, String region, String desde, String hasta) {
        return """
                {"tarjetaId":"%s","pais":"España","region":%s,"fechaDesde":"%s","fechaHasta":"%s"}
                """.formatted(id, region, desde, hasta);
    }
}
