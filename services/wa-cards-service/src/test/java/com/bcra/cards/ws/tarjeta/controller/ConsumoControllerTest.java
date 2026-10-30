package com.bcra.cards.ws.tarjeta.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bcra.cards.ws.exception.UnauthorizedException;
import com.bcra.cards.ws.tarjeta.client.CredencialesServiceClient;
import com.bcra.cards.ws.tarjeta.client.dto.ValidarCredencialResponse;
import com.bcra.cards.ws.tarjeta.entity.Consumo;
import com.bcra.cards.ws.tarjeta.entity.EstadoTarjeta;
import com.bcra.cards.ws.tarjeta.entity.MarcaTarjeta;
import com.bcra.cards.ws.tarjeta.entity.Tarjeta;
import com.bcra.cards.ws.tarjeta.entity.TipoTarjeta;
import com.bcra.cards.ws.tarjeta.repository.ConsumoRepository;
import com.bcra.cards.ws.tarjeta.repository.TarjetaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"cards.consumos.ventana-dias=7", "cards.consumos.tope-por-mensaje=2"})
@AutoConfigureMockMvc
@Transactional
class ConsumoControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private TarjetaRepository tarjetas;
    @Autowired
    private ConsumoRepository consumos;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private EntityManager entityManager;
    @MockBean
    private CredencialesServiceClient credenciales;

    private UUID tarjetaId;

    @BeforeEach
    void preparar() {
        Tarjeta tarjeta = new Tarjeta();
        tarjeta.setNumeroTarjetaHash("hash-sintetico");
        tarjeta.setUltimosCuatro("1234");
        tarjeta.setMarca(MarcaTarjeta.VISA);
        tarjeta.setTipo(TipoTarjeta.CREDITO);
        tarjeta.setTitularDocumentoTipo("DNI");
        tarjeta.setTitularDocumentoNumero("10000001");
        tarjeta.setEstado(EstadoTarjeta.ACTIVA);
        tarjeta.setActiva(true);
        tarjetaId = tarjetas.save(tarjeta).getId();
        when(credenciales.validar("sesion")).thenReturn(new ValidarCredencialResponse(true,
                "+5491100000001", "DNI", "10000001", Instant.now().plusSeconds(600)));
    }

    @Test
    @DisplayName("Respeta topes configurados y pagina fechas iguales sin duplicar ni exponer datos de tarjeta")
    void paginaConsumosDelTitular() throws Exception {
        Instant fecha = Instant.now().minus(1, ChronoUnit.DAYS);
        for (int indice = 0; indice < 3; indice++) {
            guardar(fecha, "Comercio " + indice, tarjetaId);
        }
        guardar(fecha.minus(8, ChronoUnit.DAYS), "Antiguo", tarjetaId);
        guardar(Instant.now().plus(1, ChronoUnit.DAYS), "Futuro", tarjetaId);
        guardar(fecha, "Ajeno", UUID.randomUUID());
        String primera = mvc.perform(get(ruta()).header("X-Credencial-Sesion", "sesion")
                        .param("dias", "90").param("limit", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consumos.length()").value(2))
                .andExpect(jsonPath("$.tieneMas").value(true))
                .andExpect(jsonPath("$.consumos[0].monto").value(25.50))
                .andExpect(jsonPath("$.consumos[0].moneda").value("ARS"))
                .andReturn().getResponse().getContentAsString();
        assertThat(primera).doesNotContain("PAN", "pan", "cvv", "CVV", "vencimiento", "numeroTarjetaHash",
                "ultimosCuatro", "Antiguo", "Futuro", "Ajeno");
        String cursor = json.readTree(primera).get("cursor").asText();
        String segunda = mvc.perform(get(ruta()).header("X-Credencial-Sesion", "sesion").param("cursor", cursor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consumos.length()").value(1))
                .andExpect(jsonPath("$.tieneMas").value(false)).andExpect(jsonPath("$.cursor").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(primera).doesNotContain(json.readTree(segunda).get("consumos").get(0).get("comercio").asText());
    }

    @Test
    @DisplayName("Rechaza sesión ausente o revocada con el contrato 401")
    void rechazaCredencial() throws Exception {
        mvc.perform(get(ruta())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED")).andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(credenciales);
        when(credenciales.validar("revocada")).thenThrow(new UnauthorizedException("Inválida"));
        mvc.perform(get(ruta()).header("X-Credencial-Sesion", "revocada"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Oculta la existencia de tarjetas de otro titular con 404")
    void rechazaTarjetaAjena() throws Exception {
        when(credenciales.validar("otro")).thenReturn(new ValidarCredencialResponse(true,
                "+5491100000002", "DNI", "10000002", Instant.now().plusSeconds(600)));
        mvc.perform(get(ruta()).header("X-Credencial-Sesion", "otro"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Admite una ventana menor y devuelve página vacía sin cursor")
    void ventanaMenor() throws Exception {
        guardar(Instant.now().minus(2, ChronoUnit.DAYS), "Anterior", tarjetaId);
        mvc.perform(get(ruta()).header("X-Credencial-Sesion", "sesion").param("dias", "1").param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consumos").isEmpty())
                .andExpect(jsonPath("$.tieneMas").value(false)).andExpect(jsonPath("$.cursor").isEmpty());
    }

    @Test
    @DisplayName("Rechaza parámetros no positivos y cursores inválidos sin error interno")
    void rechazaParametros() throws Exception {
        for (String parametro : new String[]{"dias", "limit"}) {
            mvc.perform(get(ruta()).header("X-Credencial-Sesion", "sesion").param(parametro, "0"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
        for (String cursor : new String[]{"!", "", codificar("otro|7|fecha|fecha|id"),
                codificar(tarjetaId + "|7|fecha|fecha|id"),
                codificar(tarjetaId + "|7|2999-01-01T00:00:00Z|2999-01-01T00:00:00Z|" + tarjetaId)}) {
            mvc.perform(get(ruta()).header("X-Credencial-Sesion", "sesion").param("cursor", cursor))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
    }

    @Test
    @DisplayName("Conserva el límite temporal del cursor ante consumos nuevos entre páginas")
    void excluyeConsumosPosterioresAlInicioDePaginacion() throws Exception {
        Instant fecha = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        guardar(fecha, "Primero", tarjetaId);
        guardar(fecha.minusSeconds(1), "Segundo", tarjetaId);
        String primera = mvc.perform(get(ruta()).header("X-Credencial-Sesion", "sesion").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumos[0].comercio").value("Primero"))
                .andExpect(jsonPath("$.consumos[0].fecha").value(fecha.toString()))
                .andExpect(jsonPath("$.tieneMas").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = json.readTree(primera).get("cursor").asText();
        guardar(Instant.now(), "Nuevo", tarjetaId);
        mvc.perform(get(ruta()).header("X-Credencial-Sesion", "sesion")
                        .param("limit", "1").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumos.length()").value(1))
                .andExpect(jsonPath("$.consumos[0].comercio").value("Segundo"))
                .andExpect(jsonPath("$.tieneMas").value(false))
                .andExpect(jsonPath("$.cursor").isEmpty());
    }

    @Test
    @DisplayName("Rechaza reutilizar un cursor con otra ventana y conserva el contrato de error")
    void rechazaCursorDeOtraVentana() throws Exception {
        guardar(Instant.now().minus(1, ChronoUnit.DAYS), "Primero", tarjetaId);
        guardar(Instant.now().minus(2, ChronoUnit.DAYS), "Segundo", tarjetaId);
        String primera = mvc.perform(get(ruta()).header("X-Credencial-Sesion", "sesion").param("limit", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String cursor = json.readTree(primera).get("cursor").asText();
        mvc.perform(get(ruta()).header("X-Credencial-Sesion", "sesion")
                        .param("dias", "3").param("cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    private String codificar(String valor) {
        return Base64.getUrlEncoder().encodeToString(valor.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String ruta() {
        return "/api/v1/tarjetas/" + tarjetaId + "/consumos";
    }

    private void guardar(Instant fecha, String comercio, UUID id) {
        Consumo consumo = new Consumo();
        consumo.setTarjetaId(id);
        consumo.setFecha(fecha);
        consumo.setComercio(comercio);
        consumo.setMonto(new BigDecimal("25.50"));
        consumo.setMoneda("ARS");
        consumos.saveAndFlush(consumo);
        entityManager.clear();
    }
}
