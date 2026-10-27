package com.bcra.accounts.ws.cuenta;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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
import com.bcra.accounts.ws.cuenta.entity.AuditoriaAccion;
import com.bcra.accounts.ws.cuenta.entity.AuditoriaResultado;
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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"confirmar", "CONFIRMAR ", " CONFIRMAR", "SI"})
    @DisplayName("Rechaza toda confirmación que no sea el literal exacto sin mover fondos")
    void rechazaConfirmacionNoExacta(String confirmacion) throws Exception {
        UUID id = preparar("100");
        String cuerpo = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(new ConfirmarTransferenciaRequest(confirmacion));

        mockMvc.perform(post("/api/v1/transferencias/{id}/confirmar", id)
                .header("X-Credencial-Sesion", "sesion").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(transferencias.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoTransferencia.PREPARADA);
        verificarSinMovimientos("10000");
    }

    @Test
    @DisplayName("La primera confirmación no renueva el vencimiento ni mueve fondos")
    void expiraEntreConfirmaciones() throws Exception {
        UUID id = preparar("600");
        when(clock.instant()).thenReturn(AHORA.plusSeconds(299));
        confirmar(id, "CONFIRMAR").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.comprobante").doesNotExist());
        verificarSinMovimientos("10000");
        assertThat(transferencias.findById(id).orElseThrow().getExpiraEn()).isEqualTo(AHORA.plusSeconds(300));

        when(clock.instant()).thenReturn(AHORA.plusSeconds(300));
        confirmar(id, "CONFIRMAR").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        verificarSinMovimientos("10000");
    }

    @Test
    @DisplayName("Revalida el saldo disponible al confirmar y conserva ambos saldos ante rechazo")
    void rechazaSaldoReducidoTrasPreparar() throws Exception {
        UUID id = preparar("100");
        Cuenta origen = cuentas.findByNumeroCuenta("origen").orElseThrow();
        origen.setSaldoDisponible(new BigDecimal("99"));
        cuentas.saveAndFlush(origen);

        confirmar(id, "CONFIRMAR").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        verificarSinMovimientos("99");
        assertThat(transferencias.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoTransferencia.PREPARADA);
        assertThat(auditorias.findAll()).filteredOn(a -> a.getAccion() == AuditoriaAccion.RECHAZAR_TRANSFERENCIA)
                .singleElement().satisfies(a -> {
                    assertThat(a.getResultado()).isEqualTo(AuditoriaResultado.RECHAZADO);
                    assertThat(a.getTransferenciaId()).isEqualTo(id);
                });
    }

    @Test
    @DisplayName("Revalida límites reducidos después de preparar sin debitar")
    void rechazaLimiteReducidoTrasPreparar() throws Exception {
        UUID id = preparar("100");
        LimiteCanal limite = limites.findAll().getFirst();
        limite.setLimiteDiario(new BigDecimal("99"));
        limites.saveAndFlush(limite);

        confirmar(id, "CONFIRMAR").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        verificarSinMovimientos("10000");
    }

    @Test
    @DisplayName("Rechaza un destino desactivado después de preparar sin debitar el origen")
    void rechazaDestinoDesactivadoTrasPreparar() throws Exception {
        UUID id = preparar("100");
        Cuenta destino = cuentas.findByNumeroCuenta("destino").orElseThrow();
        destino.setActiva(false);
        cuentas.saveAndFlush(destino);

        confirmar(id, "CONFIRMAR").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        verificarSinMovimientos("10000");
    }

    @Test
    @DisplayName("Comprobante, movimientos y auditoría corresponden a la misma ejecución persistida")
    void conservaTrazabilidadDeEjecucion() throws Exception {
        UUID id = preparar("100");

        confirmar(id, "CONFIRMAR").andExpect(status().isOk())
                .andExpect(jsonPath("$.comprobante.fecha").value(AHORA.toString()))
                .andExpect(jsonPath("$.comprobante.moneda").value("ARS"));

        var transferencia = transferencias.findById(id).orElseThrow();
        assertThat(transferencia.getEstado()).isEqualTo(EstadoTransferencia.EJECUTADA);
        assertThat(transferencia.getFechaEjecucion()).isEqualTo(AHORA);
        assertThat(transferencia.getReferencia()).isNotBlank();
        assertThat(movimientos.findAll()).hasSize(2).allSatisfy(movimiento -> {
            assertThat(movimiento.getReferencia()).isEqualTo(transferencia.getReferencia());
            assertThat(movimiento.getMonto()).isEqualByComparingTo("100");
            assertThat(movimiento.getMoneda()).isEqualTo(Moneda.ARS);
            assertThat(movimiento.getFecha()).isEqualTo(AHORA);
        });
        assertThat(movimientos.findAll()).filteredOn(m -> m.getTipo() == TipoMovimiento.DEBITO)
                .singleElement().satisfies(m -> {
                    assertThat(m.getCuenta().getId()).isEqualTo(
                            cuentas.findByNumeroCuenta("origen").orElseThrow().getId());
                    assertThat(m.getSaldoResultante()).isEqualByComparingTo("9900");
                });
        assertThat(movimientos.findAll()).filteredOn(m -> m.getTipo() == TipoMovimiento.CREDITO)
                .singleElement().satisfies(m -> {
                    assertThat(m.getCuenta().getId()).isEqualTo(
                            cuentas.findByNumeroCuenta("destino").orElseThrow().getId());
                    assertThat(m.getSaldoResultante()).isEqualByComparingTo("10100");
                });
        assertThat(auditorias.findAll()).filteredOn(a -> a.getAccion() == AuditoriaAccion.EJECUTAR_TRANSFERENCIA)
                .singleElement().satisfies(a -> {
                    assertThat(a.getTransferenciaId()).isEqualTo(id);
                    assertThat(a.getResultado()).isEqualTo(AuditoriaResultado.EXITOSO);
                });
    }

    private void verificarSinMovimientos(String disponibleOrigen) {
        Cuenta origen = cuentas.findByNumeroCuenta("origen").orElseThrow();
        Cuenta destino = cuentas.findByNumeroCuenta("destino").orElseThrow();
        assertThat(origen.getSaldoDisponible()).isEqualByComparingTo(disponibleOrigen);
        assertThat(origen.getSaldoContable()).isEqualByComparingTo("10000");
        assertThat(destino.getSaldoDisponible()).isEqualByComparingTo("10000");
        assertThat(destino.getSaldoContable()).isEqualByComparingTo("10000");
        assertThat(movimientos.count()).isZero();
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
