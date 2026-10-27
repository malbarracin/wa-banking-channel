package com.bcra.accounts.ws.cuenta;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bcra.accounts.ws.cuenta.client.CredencialesServiceClient;
import com.bcra.accounts.ws.cuenta.client.dto.ValidarCredencialResponse;
import com.bcra.accounts.ws.cuenta.entity.AuditoriaResultado;
import com.bcra.accounts.ws.cuenta.entity.Cuenta;
import com.bcra.accounts.ws.cuenta.entity.Moneda;
import com.bcra.accounts.ws.cuenta.entity.Movimiento;
import com.bcra.accounts.ws.cuenta.entity.TipoCuenta;
import com.bcra.accounts.ws.cuenta.entity.TipoMovimiento;
import com.bcra.accounts.ws.cuenta.repository.AuditoriaCuentaRepository;
import com.bcra.accounts.ws.cuenta.repository.CuentaRepository;
import com.bcra.accounts.ws.cuenta.repository.MovimientoRepository;
import com.bcra.accounts.ws.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifica HTTP, consultas reales y auditoría sobre H2 con identidad de H2 simulada. */
@SpringBootTest
@AutoConfigureMockMvc
class MovimientoIntegrationTest {

    private static final Instant FECHA = Instant.parse("2026-10-20T12:00:00Z");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private CuentaRepository cuentas;
    @Autowired
    private MovimientoRepository movimientos;
    @Autowired
    private AuditoriaCuentaRepository auditorias;
    @MockitoBean
    private CredencialesServiceClient credenciales;

    private Cuenta cuenta;

    @BeforeEach
    void preparar() {
        auditorias.deleteAll();
        movimientos.deleteAll();
        cuentas.deleteAll();
        cuenta = crearCuenta("001", "123");
        when(credenciales.validar("valida")).thenReturn(new ValidarCredencialResponse(true,
                "5491100000000", "DNI", "123", Instant.now().plusSeconds(3600)));
    }

    @Test
    @DisplayName("Pagina fechas empatadas sin duplicados y respeta el tope por defecto")
    void paginaFechasEmpatadasConTope() throws Exception {
        for (int indice = 0; indice < 12; indice++) {
            crearMovimiento(cuenta, FECHA, TipoMovimiento.CREDITO, "ref-" + indice);
        }
        crearMovimiento(crearCuenta("002", "456"), FECHA, TipoMovimiento.CREDITO, "ajeno");
        JsonNode primera = respuesta(consulta().param("desde", FECHA.minusSeconds(1).toString())
                .param("hasta", FECHA.plusSeconds(1).toString()).param("limit", "999"));
        assertThat(primera.get("movimientos").size()).isEqualTo(10);
        assertThat(primera.get("tieneMas").asBoolean()).isTrue();

        JsonNode segunda = respuesta(consulta().param("cursor", primera.get("cursor").asText()));
        assertThat(segunda.get("movimientos").size()).isEqualTo(2);
        assertThat(segunda.get("tieneMas").asBoolean()).isFalse();
        assertThat(segunda.get("cursor").isNull()).isTrue();
        Set<String> referencias = new HashSet<>();
        primera.get("movimientos").forEach(movimiento -> referencias.add(movimiento.get("referencia").asText()));
        segunda.get("movimientos").forEach(movimiento -> referencias.add(movimiento.get("referencia").asText()));
        assertThat(referencias).hasSize(12).doesNotContain("ajeno");
        assertThat(auditorias.findAll()).hasSize(2)
                .allMatch(auditoria -> auditoria.getResultado() == AuditoriaResultado.EXITOSO);
    }

    @Test
    @DisplayName("Filtra por rango inclusivo y tipo, y permite reducir el límite")
    void filtraRangoYTipo() throws Exception {
        crearMovimiento(cuenta, FECHA, TipoMovimiento.DEBITO, "debito");
        crearMovimiento(cuenta, FECHA, TipoMovimiento.CREDITO, "credito");
        crearMovimiento(cuenta, FECHA.minusSeconds(1), TipoMovimiento.DEBITO, "anterior");

        mvc.perform(consulta().param("desde", FECHA.toString()).param("hasta", FECHA.toString())
                .param("tipo", "DEBITO").param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.movimientos.length()").value(1))
                .andExpect(jsonPath("$.movimientos[0].referencia").value("debito"))
                .andExpect(jsonPath("$.movimientos[0].monto").value(1))
                .andExpect(jsonPath("$.tieneMas").value(false));
    }

    @Test
    @DisplayName("Sin filtros excluye movimientos anteriores a la ventana máxima")
    void aplicaVentanaPorDefecto() throws Exception {
        crearMovimiento(cuenta, Instant.now().minus(91, ChronoUnit.DAYS), TipoMovimiento.CREDITO, "antiguo");
        crearMovimiento(cuenta, Instant.now().minusSeconds(5), TipoMovimiento.CREDITO, "reciente");

        mvc.perform(consulta()).andExpect(status().isOk())
                .andExpect(jsonPath("$.movimientos.length()").value(1))
                .andExpect(jsonPath("$.movimientos[0].referencia").value("reciente"));
    }

    @Test
    @DisplayName("Rechaza rango invertido, ventana excesiva, límite inválido y cursor malformado")
    void rechazaFiltrosInvalidos() throws Exception {
        error(consulta().param("desde", FECHA.toString()).param("hasta", FECHA.minusSeconds(1).toString()), 400);
        error(consulta().param("desde", FECHA.minus(91, ChronoUnit.DAYS).toString())
                .param("hasta", FECHA.toString()), 400);
        error(consulta().param("limit", "0"), 400);
        error(consulta().param("cursor", "%%%"), 400);
        error(consulta().param("cursor", "YWJj"), 400);
        error(consulta().param("tipo", "OTRO"), 400);
        error(consulta().param("desde", "invalido"), 400);
    }

    @Test
    @DisplayName("El cursor no permite cambiar de cuenta o de filtros")
    void rechazaCursorDeOtraConsulta() throws Exception {
        crearMovimiento(cuenta, FECHA, TipoMovimiento.CREDITO, "uno");
        crearMovimiento(cuenta, FECHA, TipoMovimiento.CREDITO, "dos");
        JsonNode primera = respuesta(consulta().param("desde", FECHA.toString())
                .param("hasta", FECHA.toString()).param("limit", "1"));
        String cursor = primera.get("cursor").asText();
        error(consulta().param("cursor", cursor).param("tipo", "DEBITO"), 400);
        crearCuenta("003", "123");
        error(get("/api/v1/cuentas/003/movimientos").header("X-Credencial-Sesion", "valida")
                .param("cursor", cursor), 400);
    }

    @Test
    @DisplayName("Rechaza cuentas ajenas o inexistentes con el mismo error y audita el rechazo")
    void rechazaCuentasAjenas() throws Exception {
        crearCuenta("002", "456");
        error(get("/api/v1/cuentas/002/movimientos").header("X-Credencial-Sesion", "valida"), 404);
        error(get("/api/v1/cuentas/999/movimientos").header("X-Credencial-Sesion", "valida"), 404);
        assertThat(auditorias.findAll()).hasSize(2)
                .allMatch(auditoria -> auditoria.getResultado() == AuditoriaResultado.RECHAZADO);
    }

    @Test
    @DisplayName("Rechaza credencial ausente, inválida, revocada o vencida")
    void rechazaCredencialesInvalidas() throws Exception {
        error(get("/api/v1/cuentas/001/movimientos"), 401);
        when(credenciales.validar("valida")).thenThrow(new UnauthorizedException("Credencial no válida"));
        error(consulta(), 401);
        reset(credenciales);
        when(credenciales.validar("valida")).thenReturn(new ValidarCredencialResponse(false,
                "5491100000000", "DNI", "123", Instant.now().plusSeconds(3600)));
        error(consulta(), 401);
        when(credenciales.validar("valida")).thenReturn(new ValidarCredencialResponse(true,
                "5491100000000", "DNI", "123", Instant.now().minusSeconds(1)));
        error(consulta(), 401);
        assertThat(auditorias.findAll()).isEmpty();
    }

    private MockHttpServletRequestBuilder consulta() {
        return get("/api/v1/cuentas/001/movimientos").header("X-Credencial-Sesion", "valida");
    }

    private JsonNode respuesta(MockHttpServletRequestBuilder solicitud) throws Exception {
        return json.readTree(mvc.perform(solicitud).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private void error(MockHttpServletRequestBuilder solicitud, int estado) throws Exception {
        mvc.perform(solicitud).andExpect(status().is(estado)).andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.details").isArray()).andExpect(jsonPath("$.timestamp").exists());
    }

    private Cuenta crearCuenta(String numero, String documento) {
        Cuenta nueva = new Cuenta();
        nueva.setNumeroCuenta(numero);
        nueva.setAlias("Principal");
        nueva.setTipo(TipoCuenta.CA);
        nueva.setMoneda(Moneda.ARS);
        nueva.setTitularDocumentoTipo("DNI");
        nueva.setTitularDocumentoNumero(documento);
        nueva.setSaldoDisponible(BigDecimal.TEN);
        nueva.setSaldoContable(BigDecimal.TEN);
        nueva.setActiva(true);
        return cuentas.saveAndFlush(nueva);
    }

    private void crearMovimiento(Cuenta titular, Instant fecha, TipoMovimiento tipo, String referencia) {
        Movimiento movimiento = new Movimiento();
        movimiento.setCuenta(titular);
        movimiento.setFecha(fecha);
        movimiento.setTipo(tipo);
        movimiento.setMonto(BigDecimal.ONE);
        movimiento.setMoneda(Moneda.ARS);
        movimiento.setDescripcion("Movimiento de prueba");
        movimiento.setReferencia(referencia);
        movimiento.setSaldoResultante(BigDecimal.TEN);
        movimientos.saveAndFlush(movimiento);
    }
}
