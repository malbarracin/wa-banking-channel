package com.bcra.accounts.ws.cuenta;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

import com.bcra.accounts.ws.cuenta.client.CredencialesServiceClient;
import com.bcra.accounts.ws.cuenta.client.dto.ValidarCredencialResponse;
import com.bcra.accounts.ws.cuenta.dto.TransferenciaRequest;
import com.bcra.accounts.ws.cuenta.entity.AuditoriaAccion;
import com.bcra.accounts.ws.cuenta.entity.AuditoriaResultado;
import com.bcra.accounts.ws.cuenta.entity.Cuenta;
import com.bcra.accounts.ws.cuenta.entity.EstadoTransferencia;
import com.bcra.accounts.ws.cuenta.entity.LimiteCanal;
import com.bcra.accounts.ws.cuenta.entity.Moneda;
import com.bcra.accounts.ws.cuenta.entity.TerceroAgendado;
import com.bcra.accounts.ws.cuenta.entity.TipoCuenta;
import com.bcra.accounts.ws.cuenta.entity.Transferencia;
import com.bcra.accounts.ws.cuenta.exception.TransferenciaRechazadaException;
import com.bcra.accounts.ws.cuenta.repository.AuditoriaCuentaRepository;
import com.bcra.accounts.ws.cuenta.repository.CuentaRepository;
import com.bcra.accounts.ws.cuenta.repository.LimiteCanalRepository;
import com.bcra.accounts.ws.cuenta.repository.TerceroAgendadoRepository;
import com.bcra.accounts.ws.cuenta.repository.TransferenciaRepository;
import com.bcra.accounts.ws.cuenta.service.TransferenciaService;
import com.bcra.accounts.ws.exception.UnauthorizedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:transferencias;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "limits.transferencia.expiracion-preparada-minutos=7"
})
@AutoConfigureMockMvc
class TransferenciaPreparacionIntegrationTest {

    private static final Instant AHORA = Instant.parse("2026-10-27T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TransferenciaService service;
    @Autowired
    private CuentaRepository cuentas;
    @Autowired
    private TransferenciaRepository transferencias;
    @Autowired
    private TerceroAgendadoRepository terceros;
    @Autowired
    private LimiteCanalRepository limites;
    @Autowired
    private AuditoriaCuentaRepository auditorias;
    @MockitoBean
    private CredencialesServiceClient credenciales;
    @MockitoBean
    private Clock clock;

    private Cuenta origen;
    private Cuenta destino;
    private LimiteCanal limite;

    @BeforeEach
    void prepararDatos() {
        auditorias.deleteAll();
        transferencias.deleteAll();
        terceros.deleteAll();
        cuentas.deleteAll();
        limites.deleteAll();
        when(clock.instant()).thenReturn(AHORA);
        when(credenciales.validar("sesion")).thenReturn(
                new ValidarCredencialResponse(true, "+5491112345678", "DNI", "123", AHORA.plusSeconds(3600)));
        origen = guardarCuenta("origen", "123", Moneda.ARS);
        destino = guardarCuenta("destino", "123", Moneda.ARS);
        limite = new LimiteCanal();
        limite.setProducto("CA");
        limite.setCanal("WHATSAPP");
        limite.setMoneda(Moneda.ARS);
        limite.setLimiteDiario(new BigDecimal("1000"));
        limite.setLimiteMensual(new BigDecimal("2000"));
        limite.setUmbralDobleConfirmacion(new BigDecimal("500"));
        limite = limites.saveAndFlush(limite);
    }

    @Test
    @DisplayName("Prepara sin débito, devuelve resumen y vencimiento configurable y audita")
    void preparaPropiaSinDebito() throws Exception {
        solicitar(cuerpo("600")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PREPARADA"))
                .andExpect(jsonPath("$.transferenciaId").isNotEmpty())
                .andExpect(jsonPath("$.resumen").value("Transferir 600 ARS desde origen a destino"))
                .andExpect(jsonPath("$.requiereDobleConfirmacion").value(true))
                .andExpect(jsonPath("$.expiraEn").value("2026-10-27T15:07:00Z"));

        assertThat(cuentas.findById(origen.getId()).orElseThrow().getSaldoDisponible())
                .isEqualByComparingTo("10000");
        assertThat(transferencias.findAll()).singleElement().satisfies(transferencia -> {
            assertThat(transferencia.getEstado()).isEqualTo(EstadoTransferencia.PREPARADA);
            assertThat(transferencia.getExpiraEn()).isEqualTo(AHORA.plusSeconds(420));
        });
        assertThat(auditorias.findAll()).singleElement().satisfies(auditoria -> {
            assertThat(auditoria.getAccion()).isEqualTo(AuditoriaAccion.PREPARAR_TRANSFERENCIA);
            assertThat(auditoria.getResultado()).isEqualTo(AuditoriaResultado.EXITOSO);
            assertThat(auditoria.getTransferenciaId()).isNotNull();
        });
    }

    @Test
    @DisplayName("En el umbral exacto no requiere doble confirmación")
    void permiteUmbralExacto() throws Exception {
        solicitar(cuerpo("500")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiereDobleConfirmacion").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.001", "100000000000000000"})
    @DisplayName("Rechaza importes inválidos por el contrato de validación")
    void rechazaMontoInvalido(String monto) throws Exception {
        solicitar(cuerpo(monto)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
        assertThat(transferencias.count()).isZero();
    }

    @Test
    @DisplayName("Rechaza una sesión inválida antes de crear preparaciones o auditoría atribuida")
    void rechazaSesion() throws Exception {
        when(credenciales.validar("sesion")).thenThrow(new UnauthorizedException("Credencial no válida"));

        solicitar(cuerpo("1")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(transferencias.count()).isZero();
        assertThat(auditorias.count()).isZero();
    }

    @Test
    @DisplayName("No revela si una cuenta origen pertenece a otro titular")
    void rechazaOrigenAjeno() throws Exception {
        origen.setTitularDocumentoNumero("456");
        cuentas.saveAndFlush(origen);

        solicitar(cuerpo("1")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(auditorias.findAll()).singleElement()
                .extracting(auditoria -> auditoria.getResultado()).isEqualTo(AuditoriaResultado.RECHAZADO);
    }

    @Test
    @DisplayName("No permite una cuenta destino ajena como cuenta propia")
    void rechazaDestinoAjeno() throws Exception {
        destino.setTitularDocumentoNumero("456");
        cuentas.saveAndFlush(destino);

        solicitar(cuerpo("1")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-27T10:59:59Z", "2026-10-27T20:00:00Z", "2026-10-25T15:00:00Z"})
    @DisplayName("Rechaza fuera de hora o día bancario sin débito")
    void rechazaFueraDeHorario(String instante) throws Exception {
        when(clock.instant()).thenReturn(Instant.parse(instante));

        solicitar(cuerpo("1")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Horario permitido")))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
        assertThat(transferencias.count()).isZero();
    }

    @Test
    @DisplayName("Rechaza moneda distinta al origen y al destino")
    void rechazaMonedas() throws Exception {
        solicitar(cuerpo("1").replace("ARS", "USD")).andExpect(status().isConflict());
        destino.setMoneda(Moneda.USD);
        cuentas.saveAndFlush(destino);

        solicitar(cuerpo("1")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Exige destino único, distinto al origen, y cuentas activas")
    void rechazaDestinoInvalido() throws Exception {
        solicitar(cuerpo("1").replace("\"destino\"", "\"origen\"")).andExpect(status().isConflict());
        solicitar(cuerpo("1").replace("\"cuentaDestino\":\"destino\",", ""))
                .andExpect(status().isBadRequest());
        solicitar(cuerpo("1").replace("\"monto\"", "\"terceroId\":\"" + UUID.randomUUID() + "\",\"monto\""))
                .andExpect(status().isBadRequest());
        destino.setActiva(false);
        cuentas.saveAndFlush(destino);
        solicitar(cuerpo("1")).andExpect(status().isConflict());
        origen.setActiva(false);
        cuentas.saveAndFlush(origen);
        solicitar(cuerpo("1")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Solo permite terceros activos agendados por el titular")
    void validaAgenda() throws Exception {
        TerceroAgendado tercero = new TerceroAgendado();
        tercero.setAlias("proveedor");
        tercero.setTitularDocumentoTipo("DNI");
        tercero.setTitularDocumentoNumero("123");
        tercero.setNumeroCuentaDestino("externa");
        tercero.setActivo(true);
        tercero = terceros.saveAndFlush(tercero);
        String solicitud = cuerpo("1").replace("\"cuentaDestino\":\"destino\"",
                "\"terceroId\":\"" + tercero.getId() + "\"");

        solicitar(solicitud).andExpect(status().isCreated())
                .andExpect(jsonPath("$.resumen").value("Transferir 1 ARS desde origen a proveedor"));
        tercero.setActivo(false);
        terceros.saveAndFlush(tercero);
        solicitar(solicitud).andExpect(status().isConflict());
        tercero.setActivo(true);
        tercero.setTitularDocumentoNumero("456");
        terceros.saveAndFlush(tercero);
        solicitar(solicitud).andExpect(status().isConflict());
        solicitar(solicitud.replace(tercero.getId().toString(), UUID.randomUUID().toString()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Límite diario incluye preparaciones previas y permite el máximo exacto")
    void validaAcumuladoDiario() throws Exception {
        solicitar(cuerpo("600")).andExpect(status().isCreated());
        solicitar(cuerpo("400")).andExpect(status().isCreated());
        solicitar(cuerpo("0.01")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Límite diario superado"));
    }

    @Test
    @DisplayName("Límite mensual suma ejecutadas previas de otras cuentas del mismo producto")
    void validaAcumuladoMensualEntreCuentas() throws Exception {
        guardarHistorial(destino, "1500", EstadoTransferencia.EJECUTADA,
                Instant.parse("2026-10-01T15:00:00Z"), AHORA.minusSeconds(100));
        solicitar(cuerpo("501")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Límite mensual superado"));
    }

    @Test
    @DisplayName("Preparaciones vencidas y rechazadas no consumen límites ni saldo")
    void liberaPreparacionesVencidas() throws Exception {
        guardarHistorial(origen, "10000", EstadoTransferencia.PREPARADA, AHORA, AHORA);
        guardarHistorial(origen, "10000", EstadoTransferencia.RECHAZADA, AHORA, AHORA.plusSeconds(500));

        solicitar(cuerpo("1000")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Saldo insuficiente considera reservas vigentes sin debitar")
    void validaSaldoConReservas() throws Exception {
        origen.setSaldoDisponible(new BigDecimal("100"));
        cuentas.saveAndFlush(origen);
        solicitar(cuerpo("80")).andExpect(status().isCreated());
        solicitar(cuerpo("21")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Saldo disponible insuficiente"));
        assertThat(cuentas.findById(origen.getId()).orElseThrow().getSaldoDisponible())
                .isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Sin política habilitada rechaza la operación")
    void rechazaSinLimites() throws Exception {
        limites.deleteAll();

        solicitar(cuerpo("1")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Dos preparaciones concurrentes no pueden comprometer más saldo del disponible")
    void serializaPreparacionesConcurrentes() throws Exception {
        origen.setSaldoDisponible(new BigDecimal("100"));
        cuentas.saveAndFlush(origen);
        CountDownLatch inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primera = executor.submit(() -> prepararConcurrente(inicio));
            var segunda = executor.submit(() -> prepararConcurrente(inicio));
            inicio.countDown();

            assertThat(List.of(primera.get(10, TimeUnit.SECONDS), segunda.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(transferencias.count()).isEqualTo(1);
        assertThat(auditorias.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Una reserva confirmada vigente reduce el saldo disponible para nuevas preparaciones")
    void descuentaReservaConfirmadaDelSaldo() throws Exception {
        origen.setSaldoDisponible(new BigDecimal("100"));
        cuentas.saveAndFlush(origen);
        guardarHistorial(origen, "80", EstadoTransferencia.CONFIRMADA, AHORA, AHORA.plusSeconds(300));

        solicitar(cuerpo("21")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Saldo disponible insuficiente"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(transferencias.count()).isEqualTo(1);
        assertThat(cuentas.findById(origen.getId()).orElseThrow().getSaldoDisponible())
                .isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("El límite diario incluye reservas confirmadas de otra cuenta del titular")
    void acumulaConfirmadasEntreCuentas() throws Exception {
        guardarHistorial(destino, "900", EstadoTransferencia.CONFIRMADA, AHORA, AHORA.plusSeconds(300));

        solicitar(cuerpo("101")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Límite diario superado"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(transferencias.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("El límite mensual permite el máximo exacto y rechaza el siguiente centavo")
    void permiteLimiteMensualExacto() throws Exception {
        guardarHistorial(destino, "1500", EstadoTransferencia.EJECUTADA,
                Instant.parse("2026-10-01T15:00:00Z"), AHORA.minusSeconds(100));

        solicitar(cuerpo("500")).andExpect(status().isCreated());
        solicitar(cuerpo("0.01")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Límite mensual superado"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(transferencias.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Las transferencias ejecutadas del mes anterior no consumen los límites actuales")
    void excluyeEjecucionesDelMesAnterior() throws Exception {
        guardarHistorial(origen, "10000", EstadoTransferencia.EJECUTADA,
                Instant.parse("2026-09-30T15:00:00Z"), AHORA.minusSeconds(100));

        solicitar(cuerpo("1000")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PREPARADA"));

        assertThat(transferencias.count()).isEqualTo(2);
    }

    private boolean prepararConcurrente(CountDownLatch inicio) throws InterruptedException {
        inicio.await(5, TimeUnit.SECONDS);
        try {
            service.preparar("sesion", new TransferenciaRequest("origen", "destino", null,
                    new BigDecimal("80"), Moneda.ARS, null));
            return true;
        } catch (TransferenciaRechazadaException rechazo) {
            return false;
        }
    }

    private org.springframework.test.web.servlet.ResultActions solicitar(String cuerpo) throws Exception {
        return mockMvc.perform(post("/api/v1/transferencias").header("X-Credencial-Sesion", "sesion")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private String cuerpo(String monto) {
        return "{\"cuentaOrigen\":\"origen\",\"cuentaDestino\":\"destino\",\"monto\":" + monto
                + ",\"moneda\":\"ARS\"}";
    }

    private Cuenta guardarCuenta(String numero, String documento, Moneda moneda) {
        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta(numero);
        cuenta.setAlias(numero);
        cuenta.setTipo(TipoCuenta.CA);
        cuenta.setMoneda(moneda);
        cuenta.setTitularDocumentoTipo("DNI");
        cuenta.setTitularDocumentoNumero(documento);
        cuenta.setSaldoDisponible(new BigDecimal("10000"));
        cuenta.setSaldoContable(new BigDecimal("10000"));
        cuenta.setActiva(true);
        return cuentas.saveAndFlush(cuenta);
    }

    private void guardarHistorial(Cuenta cuenta, String monto, EstadoTransferencia estado,
            Instant fecha, Instant vencimiento) {
        Transferencia transferencia = new Transferencia();
        transferencia.setCuentaOrigen(cuenta);
        transferencia.setCuentaDestino(cuenta.getId().equals(origen.getId()) ? destino : origen);
        transferencia.setMonto(new BigDecimal(monto));
        transferencia.setMoneda(Moneda.ARS);
        transferencia.setEstado(estado);
        transferencia.setFechaEjecucion(fecha);
        transferencia.setExpiraEn(vencimiento);
        transferencias.saveAndFlush(transferencia);
    }
}

