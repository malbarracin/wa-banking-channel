package com.bcra.accounts.ws.cuenta;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
import com.bcra.accounts.ws.cuenta.entity.Cuenta;
import com.bcra.accounts.ws.cuenta.entity.Moneda;
import com.bcra.accounts.ws.cuenta.entity.Movimiento;
import com.bcra.accounts.ws.cuenta.entity.TipoCuenta;
import com.bcra.accounts.ws.cuenta.entity.TipoMovimiento;
import com.bcra.accounts.ws.cuenta.repository.AuditoriaCuentaRepository;
import com.bcra.accounts.ws.cuenta.repository.CuentaRepository;
import com.bcra.accounts.ws.cuenta.repository.MovimientoRepository;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "limits.movimientos.tope-por-mensaje=2",
        "limits.movimientos.ventana-dias=7"
})
@AutoConfigureMockMvc
class MovimientoConfiguracionIntegrationTest {

    private static final Instant FECHA = Instant.parse("2026-10-20T12:00:00Z");
    private static final String CREDENCIAL = "credencial-configuracion";
    private static final int VENTANA_DIAS = 7;

    private final MockMvc mvc;
    private final CuentaRepository cuentas;
    private final MovimientoRepository movimientos;
    private final AuditoriaCuentaRepository auditorias;

    @MockitoBean
    private CredencialesServiceClient credenciales;

    private Cuenta cuenta;

    @Autowired
    MovimientoConfiguracionIntegrationTest(MockMvc mvc, CuentaRepository cuentas,
            MovimientoRepository movimientos, AuditoriaCuentaRepository auditorias) {
        this.mvc = mvc;
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.auditorias = auditorias;
    }

    @BeforeEach
    void preparar() {
        auditorias.deleteAll();
        movimientos.deleteAll();
        cuentas.deleteAll();
        cuenta = new Cuenta();
        cuenta.setNumeroCuenta("configurada");
        cuenta.setAlias("Principal");
        cuenta.setTipo(TipoCuenta.CA);
        cuenta.setMoneda(Moneda.ARS);
        cuenta.setTitularDocumentoTipo("DNI");
        cuenta.setTitularDocumentoNumero("123");
        cuenta.setSaldoDisponible(BigDecimal.TEN);
        cuenta.setSaldoContable(BigDecimal.TEN);
        cuenta.setActiva(true);
        cuenta = cuentas.saveAndFlush(cuenta);
        when(credenciales.validar(CREDENCIAL)).thenReturn(new ValidarCredencialResponse(true,
                "5491100000000", "DNI", "123", Instant.now().plus(1, ChronoUnit.HOURS)));
    }

    @Test
    @DisplayName("Usa el tope configurado cuando se omite el límite o se solicita uno superior")
    void aplicaTopeConfigurado() throws Exception {
        crearMovimiento(FECHA, "primero");
        crearMovimiento(FECHA.minusSeconds(1), "segundo");
        crearMovimiento(FECHA.minusSeconds(2), "tercero");

        mvc.perform(consulta()).andExpect(status().isOk())
                .andExpect(jsonPath("$.movimientos.length()").value(2))
                .andExpect(jsonPath("$.movimientos[0].referencia").value("primero"))
                .andExpect(jsonPath("$.movimientos[1].referencia").value("segundo"))
                .andExpect(jsonPath("$.tieneMas").value(true))
                .andExpect(jsonPath("$.cursor").isString());
        mvc.perform(consulta().param("limit", "10")).andExpect(status().isOk())
                .andExpect(jsonPath("$.movimientos.length()").value(2))
                .andExpect(jsonPath("$.tieneMas").value(true));
    }

    @Test
    @DisplayName("Acepta exactamente la ventana configurada e incluye ambos extremos")
    void incluyeExtremosDeVentanaConfigurada() throws Exception {
        Instant inicio = FECHA.minus(VENTANA_DIAS, ChronoUnit.DAYS);
        crearMovimiento(inicio, "inicio");
        crearMovimiento(FECHA, "fin");
        crearMovimiento(inicio.minusSeconds(1), "fuera");

        mvc.perform(consulta()).andExpect(status().isOk())
                .andExpect(jsonPath("$.movimientos.length()").value(2))
                .andExpect(jsonPath("$.movimientos[0].referencia").value("fin"))
                .andExpect(jsonPath("$.movimientos[1].referencia").value("inicio"))
                .andExpect(jsonPath("$.tieneMas").value(false))
                .andExpect(jsonPath("$.cursor").isEmpty());
    }

    @Test
    @DisplayName("Rechaza un segundo adicional sobre la ventana configurada con el contrato de error")
    void rechazaExcesoDeVentanaConfigurada() throws Exception {
        mvc.perform(get("/api/v1/cuentas/configurada/movimientos")
                        .header("X-Credencial-Sesion", CREDENCIAL)
                        .param("hasta", FECHA.toString())
                        .param("desde", FECHA.minus(VENTANA_DIAS, ChronoUnit.DAYS).minusSeconds(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("Una cuenta sin movimientos devuelve una página vacía sin cursor")
    void devuelvePaginaVacia() throws Exception {
        mvc.perform(consulta()).andExpect(status().isOk())
                .andExpect(jsonPath("$.movimientos").isEmpty())
                .andExpect(jsonPath("$.tieneMas").value(false))
                .andExpect(jsonPath("$.cursor").isEmpty());
    }

    private MockHttpServletRequestBuilder consulta() {
        return get("/api/v1/cuentas/configurada/movimientos").header("X-Credencial-Sesion", CREDENCIAL)
                .param("desde", FECHA.minus(VENTANA_DIAS, ChronoUnit.DAYS).toString())
                .param("hasta", FECHA.toString());
    }

    private void crearMovimiento(Instant fecha, String referencia) {
        Movimiento movimiento = new Movimiento();
        movimiento.setCuenta(cuenta);
        movimiento.setFecha(fecha);
        movimiento.setTipo(TipoMovimiento.CREDITO);
        movimiento.setMonto(BigDecimal.ONE);
        movimiento.setMoneda(Moneda.ARS);
        movimiento.setDescripcion("Movimiento de prueba");
        movimiento.setReferencia(referencia);
        movimiento.setSaldoResultante(BigDecimal.TEN);
        movimientos.saveAndFlush(movimiento);
    }
}
