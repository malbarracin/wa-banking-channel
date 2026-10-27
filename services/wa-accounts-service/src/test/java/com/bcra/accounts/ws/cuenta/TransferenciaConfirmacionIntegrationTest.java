package com.bcra.accounts.ws.cuenta;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bcra.accounts.ws.cuenta.client.CredencialesServiceClient;
import com.bcra.accounts.ws.cuenta.client.dto.ValidarCredencialResponse;
import com.bcra.accounts.ws.cuenta.dto.ConfirmarTransferenciaRequest;
import com.bcra.accounts.ws.cuenta.dto.TransferenciaRequest;
import com.bcra.accounts.ws.cuenta.entity.Cuenta;
import com.bcra.accounts.ws.cuenta.entity.EstadoTransferencia;
import com.bcra.accounts.ws.cuenta.entity.LimiteCanal;
import com.bcra.accounts.ws.cuenta.entity.Moneda;
import com.bcra.accounts.ws.cuenta.entity.TerceroAgendado;
import com.bcra.accounts.ws.cuenta.entity.TipoCuenta;
import com.bcra.accounts.ws.cuenta.entity.TipoMovimiento;
import com.bcra.accounts.ws.cuenta.repository.AuditoriaCuentaRepository;
import com.bcra.accounts.ws.cuenta.repository.CuentaRepository;
import com.bcra.accounts.ws.cuenta.repository.LimiteCanalRepository;
import com.bcra.accounts.ws.cuenta.repository.MovimientoRepository;
import com.bcra.accounts.ws.cuenta.repository.TerceroAgendadoRepository;
import com.bcra.accounts.ws.cuenta.repository.TransferenciaRepository;
import com.bcra.accounts.ws.cuenta.service.TransferenciaService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:confirmacion;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class TransferenciaConfirmacionIntegrationTest {

    private static final Instant AHORA = Instant.parse("2026-10-27T15:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private TransferenciaService service;
    @Autowired private CuentaRepository cuentas;
    @Autowired private TransferenciaRepository transferencias;
    @Autowired private MovimientoRepository movimientos;
    @Autowired private TerceroAgendadoRepository terceros;
    @Autowired private LimiteCanalRepository limites;
    @Autowired private AuditoriaCuentaRepository auditorias;
    @MockitoBean private CredencialesServiceClient credenciales;
    @MockitoBean private Clock clock;

    @BeforeEach
    void prepararDatos() {
        auditorias.deleteAll();
        movimientos.deleteAll();
        transferencias.deleteAll();
        terceros.deleteAll();
        cuentas.deleteAll();
        limites.deleteAll();
        when(clock.instant()).thenReturn(AHORA);
        when(credenciales.validar("sesion")).thenReturn(
                new ValidarCredencialResponse(true, "+5491112345678", "DNI", "123", AHORA.plusSeconds(3600)));
        guardarCuenta("origen", "123");
        guardarCuenta("destino", "123");
        LimiteCanal limite = new LimiteCanal();
        limite.setProducto("CA");
        limite.setCanal("WHATSAPP");
        limite.setMoneda(Moneda.ARS);
        limite.setLimiteDiario(new BigDecimal("2000"));
        limite.setLimiteMensual(new BigDecimal("10000"));
        limite.setUmbralDobleConfirmacion(new BigDecimal("500"));
        limites.saveAndFlush(limite);
    }

    @Test
    @DisplayName("Ejecuta por HTTP, acredita, debita, emite comprobante y rechaza reejecución")
    void ejecutaUnaSolaVez() throws Exception {
        UUID id = preparar("100");
        confirmar(id, "CONFIRMAR").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EJECUTADA"))
                .andExpect(jsonPath("$.referencia").isNotEmpty())
                .andExpect(jsonPath("$.comprobante.origen").value("origen"))
                .andExpect(jsonPath("$.comprobante.destino").value("destino"))
                .andExpect(jsonPath("$.comprobante.monto").value(100));
        confirmar(id, "CONFIRMAR").andExpect(status().isConflict());
        assertThat(cuentas.findByNumeroCuenta("origen").orElseThrow().getSaldoDisponible())
                .isEqualByComparingTo("9900");
        assertThat(cuentas.findByNumeroCuenta("destino").orElseThrow().getSaldoContable())
                .isEqualByComparingTo("10100");
        assertThat(movimientos.findAll()).extracting(movimiento -> movimiento.getTipo())
                .containsExactlyInAnyOrder(TipoMovimiento.DEBITO, TipoMovimiento.CREDITO);
    }

    @Test
    @DisplayName("Exige dos CONFIRMAR consecutivos y reinicia ante otra respuesta")
    void exigeDobleConfirmacionConsecutiva() throws Exception {
        UUID id = preparar("600");
        confirmar(id, "CONFIRMAR").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        assertThat(movimientos.count()).isZero();
        confirmar(id, "confirmar").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
        confirmar(id, "CONFIRMAR").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        confirmar(id, "CONFIRMAR").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EJECUTADA"));
    }

    @Test
    @DisplayName("Rechaza al alcanzar el vencimiento sin modificar fondos")
    void rechazaExpirada() throws Exception {
        UUID id = preparar("100");
        when(clock.instant()).thenReturn(AHORA.plusSeconds(300));
        confirmar(id, "CONFIRMAR").andExpect(status().isConflict());
        assertThat(movimientos.count()).isZero();
    }

    @Test
    @DisplayName("Oculta transferencias ajenas")
    void rechazaAjena() throws Exception {
        UUID id = preparar("100");
        when(credenciales.validar("sesion")).thenReturn(
                new ValidarCredencialResponse(true, "+5491112345678", "DNI", "999", AHORA.plusSeconds(3600)));
        confirmar(id, "CONFIRMAR").andExpect(status().isNotFound());
        assertThat(movimientos.count()).isZero();
    }

    @Test
    @DisplayName("Dos confirmaciones concurrentes simples producen un solo débito")
    void serializaConfirmaciones() throws Exception {
        UUID id = preparar("100");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primera = executor.submit(() -> confirmar(id, "CONFIRMAR").andReturn().getResponse().getStatus());
            var segunda = executor.submit(() -> confirmar(id, "CONFIRMAR").andReturn().getResponse().getStatus());
            assertThat(java.util.List.of(primera.get(), segunda.get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(movimientos.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Acredita al tercero local agendado")
    void ejecutaTercero() {
        guardarCuenta("tercero", "999");
        TerceroAgendado tercero = new TerceroAgendado();
        tercero.setAlias("tercero");
        tercero.setNumeroCuentaDestino("tercero");
        tercero.setTitularDocumentoTipo("DNI");
        tercero.setTitularDocumentoNumero("123");
        tercero.setActivo(true);
        tercero = terceros.saveAndFlush(tercero);
        UUID id = service.preparar("sesion", new TransferenciaRequest("origen", null, tercero.getId(),
                new BigDecimal("100"), Moneda.ARS, "Pago")).transferenciaId();
        assertThat(service.confirmar("sesion", id, new ConfirmarTransferenciaRequest("CONFIRMAR")).estado())
                .isEqualTo(EstadoTransferencia.EJECUTADA);
        assertThat(cuentas.findByNumeroCuenta("tercero").orElseThrow().getSaldoDisponible())
                .isEqualByComparingTo("10100");
    }

    private UUID preparar(String monto) {
        return service.preparar("sesion", new TransferenciaRequest("origen", "destino", null,
                new BigDecimal(monto), Moneda.ARS, "Pago")).transferenciaId();
    }

    private org.springframework.test.web.servlet.ResultActions confirmar(UUID id, String confirmacion)
            throws Exception {
        return mockMvc.perform(post("/api/v1/transferencias/{id}/confirmar", id)
                .header("X-Credencial-Sesion", "sesion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmacion\":\"" + confirmacion + "\"}"));
    }

    private void guardarCuenta(String numero, String documento) {
        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta(numero);
        cuenta.setAlias(numero);
        cuenta.setTipo(TipoCuenta.CA);
        cuenta.setMoneda(Moneda.ARS);
        cuenta.setTitularDocumentoTipo("DNI");
        cuenta.setTitularDocumentoNumero(documento);
        cuenta.setSaldoDisponible(new BigDecimal("10000"));
        cuenta.setSaldoContable(new BigDecimal("10000"));
        cuenta.setActiva(true);
        cuentas.saveAndFlush(cuenta);
    }
}
