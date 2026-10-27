package com.bcra.accounts.ws.cuenta.repository;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.bcra.accounts.ws.cuenta.entity.AuditoriaAccion;
import com.bcra.accounts.ws.cuenta.entity.AuditoriaCuenta;
import com.bcra.accounts.ws.cuenta.entity.AuditoriaResultado;
import com.bcra.accounts.ws.cuenta.entity.Cuenta;
import com.bcra.accounts.ws.cuenta.entity.EstadoTransferencia;
import com.bcra.accounts.ws.cuenta.entity.LimiteCanal;
import com.bcra.accounts.ws.cuenta.entity.Moneda;
import com.bcra.accounts.ws.cuenta.entity.Movimiento;
import com.bcra.accounts.ws.cuenta.entity.TerceroAgendado;
import com.bcra.accounts.ws.cuenta.entity.TipoCuenta;
import com.bcra.accounts.ws.cuenta.entity.TipoMovimiento;
import com.bcra.accounts.ws.cuenta.entity.Transferencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifica el esquema JPA y las consultas sobre datos persistidos. */
@DataJpaTest
class CuentaRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private CuentaRepository cuentas;
    @Autowired
    private MovimientoRepository movimientos;
    @Autowired
    private TransferenciaRepository transferencias;
    @Autowired
    private TerceroAgendadoRepository terceros;
    @Autowired
    private LimiteCanalRepository limites;
    @Autowired
    private AuditoriaCuentaRepository auditorias;

    @Test
    @DisplayName("Persiste cuentas y movimientos y filtra por titular, fecha y tipo")
    void persisteYFiltraCuentasYMovimientos() {
        Cuenta cuenta = cuentas.saveAndFlush(crearCuenta("001", "123"));
        cuentas.saveAndFlush(crearCuenta("002", "456"));
        Movimiento movimiento = new Movimiento();
        movimiento.setCuenta(cuenta);
        movimiento.setFecha(Instant.parse("2026-10-27T12:00:00Z"));
        movimiento.setTipo(TipoMovimiento.DEBITO);
        movimiento.setMonto(new BigDecimal("10.25"));
        movimiento.setMoneda(Moneda.ARS);
        movimiento.setDescripcion("Transferencia");
        movimiento.setSaldoResultante(new BigDecimal("89.75"));
        movimientos.saveAndFlush(movimiento);
        entityManager.clear();

        assertThat(cuentas.findByTitularDocumentoTipoAndTitularDocumentoNumero("DNI", "123"))
                .extracting(Cuenta::getNumeroCuenta).containsExactly("001");
        assertThat(cuentas.findByNumeroCuentaAndTitularDocumentoTipoAndTitularDocumentoNumero("001", "DNI", "456"))
                .isEmpty();
        Cuenta recuperada = cuentas.findByNumeroCuenta("001").orElseThrow();
        assertThat(recuperada.getSaldoDisponible()).isEqualByComparingTo("100.00");
        assertThat(recuperada.getCreatedAt()).isNotNull();
        assertThat(recuperada.getUpdatedAt()).isNotNull();
        assertThat(movimientos.findByCuentaIdOrderByFechaDesc(cuenta.getId(), PageRequest.of(0, 10)))
                .extracting(Movimiento::getMonto).containsExactly(new BigDecimal("10.25"));
        assertThat(movimientos.findByCuentaIdAndFechaBetweenAndTipo(cuenta.getId(),
                Instant.parse("2026-10-27T00:00:00Z"), Instant.parse("2026-10-28T00:00:00Z"),
                TipoMovimiento.CREDITO, PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    @DisplayName("Persiste transferencias propias y a terceros con sus relaciones")
    void persisteTransferenciasConRelaciones() {
        Cuenta origen = cuentas.saveAndFlush(crearCuenta("001", "123"));
        Cuenta destino = cuentas.saveAndFlush(crearCuenta("002", "123"));
        TerceroAgendado tercero = new TerceroAgendado();
        tercero.setTitularDocumentoTipo("DNI");
        tercero.setTitularDocumentoNumero("123");
        tercero.setAlias("Destinatario");
        tercero.setNumeroCuentaDestino("003");
        tercero.setActivo(true);
        terceros.saveAndFlush(tercero);
        Transferencia propia = crearTransferencia(origen);
        propia.setCuentaDestino(destino);
        transferencias.saveAndFlush(propia);
        Transferencia externa = crearTransferencia(origen);
        externa.setTercero(tercero);
        transferencias.saveAndFlush(externa);
        entityManager.clear();

        Transferencia recuperada = transferencias.findByIdAndEstado(propia.getId(), EstadoTransferencia.PREPARADA)
                .orElseThrow();
        assertThat(recuperada.getCuentaOrigen().getNumeroCuenta()).isEqualTo("001");
        assertThat(recuperada.getCuentaDestino().getNumeroCuenta()).isEqualTo("002");
        assertThat(transferencias.findById(externa.getId()).orElseThrow().getTercero().getNumeroCuentaDestino())
                .isEqualTo("003");
        assertThat(transferencias.findByCuentaOrigenIdAndFechaCreacionAfter(origen.getId(), Instant.EPOCH))
                .hasSize(2);
        assertThat(terceros.findByTitularDocumentoTipoAndTitularDocumentoNumeroAndActivoTrue("DNI", "123"))
                .hasSize(1);
        assertThat(terceros.findByTitularDocumentoTipoAndTitularDocumentoNumeroAndActivoTrue("DNI", "456"))
                .isEmpty();
    }

    @Test
    @DisplayName("Persiste límites y auditorías, incluso rechazos sin cuenta asociada")
    void persisteLimitesYAuditorias() {
        LimiteCanal limite = new LimiteCanal();
        limite.setProducto("CA");
        limite.setCanal("WHATSAPP");
        limite.setMoneda(Moneda.ARS);
        limite.setLimiteDiario(new BigDecimal("1000.00"));
        limite.setLimiteMensual(new BigDecimal("10000.00"));
        limite.setUmbralDobleConfirmacion(new BigDecimal("500.00"));
        limites.saveAndFlush(limite);
        AuditoriaCuenta auditoria = new AuditoriaCuenta();
        auditoria.setAccion(AuditoriaAccion.RECHAZAR_TRANSFERENCIA);
        auditoria.setResultado(AuditoriaResultado.RECHAZADO);
        auditoria.setNumeroWhatsapp("5491100000000");
        auditoria.setDocumentoTipo("DNI");
        auditoria.setDocumentoNumero("123");
        auditoria.setMotivo("Límite superado");
        auditorias.saveAndFlush(auditoria);
        entityManager.clear();

        assertThat(limites.findByProductoAndCanal("CA", "WHATSAPP").orElseThrow().getLimiteDiario())
                .isEqualByComparingTo("1000.00");
        assertThat(auditorias.findByNumeroWhatsappOrderByFechaDesc("5491100000000", PageRequest.of(0, 10)))
                .allSatisfy(registro -> {
                    assertThat(registro.getFecha()).isNotNull();
                    assertThat(registro.getResultado()).isEqualTo(AuditoriaResultado.RECHAZADO);
                    assertThat(registro.getCuentaId()).isNull();
                }).hasSize(1);
    }

    @Test
    @DisplayName("Rechaza números de cuenta duplicados")
    void rechazaNumeroCuentaDuplicado() {
        cuentas.saveAndFlush(crearCuenta("001", "123"));

        assertThatThrownBy(() -> cuentas.saveAndFlush(crearCuenta("001", "456")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Rechaza movimientos sin cuenta")
    void rechazaMovimientoSinCuenta() {
        Movimiento movimiento = new Movimiento();
        movimiento.setFecha(Instant.now());
        movimiento.setTipo(TipoMovimiento.CREDITO);
        movimiento.setMonto(BigDecimal.ONE);
        movimiento.setMoneda(Moneda.USD);
        movimiento.setDescripcion("Crédito");
        movimiento.setSaldoResultante(BigDecimal.ONE);

        assertThatThrownBy(() -> movimientos.saveAndFlush(movimiento))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Rechaza transferencias sin destinatario")
    void rechazaTransferenciaSinDestinatario() {
        Cuenta origen = cuentas.saveAndFlush(crearCuenta("001", "123"));

        assertThatThrownBy(() -> transferencias.saveAndFlush(crearTransferencia(origen)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Rechaza límites duplicados para el mismo producto y canal")
    void rechazaLimiteDuplicado() {
        LimiteCanal primero = crearLimite();
        limites.saveAndFlush(primero);

        assertThatThrownBy(() -> limites.saveAndFlush(crearLimite()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Rechaza transferencias con cuenta destino y tercero simultáneamente")
    void rechazaTransferenciaConDosDestinatarios() {
        Cuenta origen = cuentas.saveAndFlush(crearCuenta("001", "123"));
        Cuenta destino = cuentas.saveAndFlush(crearCuenta("002", "123"));
        TerceroAgendado tercero = new TerceroAgendado();
        tercero.setTitularDocumentoTipo("DNI");
        tercero.setTitularDocumentoNumero("123");
        tercero.setAlias("Destinatario");
        tercero.setNumeroCuentaDestino("003");
        tercero.setActivo(true);
        terceros.saveAndFlush(tercero);
        Transferencia transferencia = crearTransferencia(origen);
        transferencia.setCuentaDestino(destino);
        transferencia.setTercero(tercero);

        assertThatThrownBy(() -> transferencias.saveAndFlush(transferencia))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Excluye terceros inactivos y titulares con distinto tipo de documento")
    void filtraTercerosPorEstadoYDocumentoCompleto() {
        TerceroAgendado activo = new TerceroAgendado();
        activo.setTitularDocumentoTipo("DNI");
        activo.setTitularDocumentoNumero("123");
        activo.setAlias("Activo");
        activo.setNumeroCuentaDestino("001");
        activo.setActivo(true);
        terceros.saveAndFlush(activo);
        TerceroAgendado inactivo = new TerceroAgendado();
        inactivo.setTitularDocumentoTipo("DNI");
        inactivo.setTitularDocumentoNumero("123");
        inactivo.setAlias("Inactivo");
        inactivo.setNumeroCuentaDestino("002");
        inactivo.setActivo(false);
        terceros.saveAndFlush(inactivo);
        entityManager.clear();

        assertThat(terceros.findByTitularDocumentoTipoAndTitularDocumentoNumeroAndActivoTrue("DNI", "123"))
                .extracting(TerceroAgendado::getId).containsExactly(activo.getId());
        assertThat(terceros.findByTitularDocumentoTipoAndTitularDocumentoNumeroAndActivoTrue("PASAPORTE", "123"))
                .isEmpty();
    }

    @Test
    @DisplayName("Pagina movimientos del más reciente al más antiguo sin incluir otras cuentas")
    void paginaMovimientosEnOrdenDescendentePorCuenta() {
        Cuenta cuenta = cuentas.saveAndFlush(crearCuenta("001", "123"));
        Cuenta otraCuenta = cuentas.saveAndFlush(crearCuenta("002", "456"));
        Instant inicio = Instant.parse("2026-10-27T12:00:00Z");
        movimientos.saveAndFlush(crearMovimiento(cuenta, inicio));
        movimientos.saveAndFlush(crearMovimiento(cuenta, inicio.plusSeconds(1)));
        movimientos.saveAndFlush(crearMovimiento(otraCuenta, inicio.plusSeconds(2)));
        entityManager.clear();

        Page<Movimiento> primeraPagina = movimientos.findByCuentaIdOrderByFechaDesc(
                cuenta.getId(), PageRequest.of(0, 1));
        Page<Movimiento> segundaPagina = movimientos.findByCuentaIdOrderByFechaDesc(
                cuenta.getId(), PageRequest.of(1, 1));

        assertThat(primeraPagina.getTotalElements()).isEqualTo(2);
        assertThat(primeraPagina).extracting(Movimiento::getFecha).containsExactly(inicio.plusSeconds(1));
        assertThat(segundaPagina).extracting(Movimiento::getFecha).containsExactly(inicio);
        assertThat(primeraPagina.hasNext()).isTrue();
        assertThat(segundaPagina.hasNext()).isFalse();
    }

    private Movimiento crearMovimiento(Cuenta cuenta, Instant fecha) {
        Movimiento movimiento = new Movimiento();
        movimiento.setCuenta(cuenta);
        movimiento.setFecha(fecha);
        movimiento.setTipo(TipoMovimiento.CREDITO);
        movimiento.setMonto(BigDecimal.ONE);
        movimiento.setMoneda(Moneda.ARS);
        movimiento.setDescripcion("Crédito");
        movimiento.setSaldoResultante(BigDecimal.TEN);
        return movimiento;
    }

    private LimiteCanal crearLimite() {
        LimiteCanal limite = new LimiteCanal();
        limite.setProducto("CA");
        limite.setCanal("WHATSAPP");
        limite.setMoneda(Moneda.ARS);
        limite.setLimiteDiario(new BigDecimal("1000.00"));
        limite.setLimiteMensual(new BigDecimal("10000.00"));
        limite.setUmbralDobleConfirmacion(new BigDecimal("500.00"));
        return limite;
    }

    private Cuenta crearCuenta(String numeroCuenta, String documento) {
        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta(numeroCuenta);
        cuenta.setAlias("Cuenta principal");
        cuenta.setTipo(TipoCuenta.CA);
        cuenta.setMoneda(Moneda.ARS);
        cuenta.setTitularDocumentoTipo("DNI");
        cuenta.setTitularDocumentoNumero(documento);
        cuenta.setSaldoDisponible(new BigDecimal("100.00"));
        cuenta.setSaldoContable(new BigDecimal("100.00"));
        cuenta.setActiva(true);
        return cuenta;
    }

    private Transferencia crearTransferencia(Cuenta origen) {
        Transferencia transferencia = new Transferencia();
        transferencia.setCuentaOrigen(origen);
        transferencia.setMonto(BigDecimal.TEN);
        transferencia.setMoneda(Moneda.ARS);
        transferencia.setEstado(EstadoTransferencia.PREPARADA);
        return transferencia;
    }
}
