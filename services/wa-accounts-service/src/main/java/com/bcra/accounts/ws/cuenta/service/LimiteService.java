package com.bcra.accounts.ws.cuenta.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;

import com.bcra.accounts.ws.cuenta.entity.Cuenta;
import com.bcra.accounts.ws.cuenta.entity.EstadoTransferencia;
import com.bcra.accounts.ws.cuenta.entity.LimiteCanal;
import com.bcra.accounts.ws.cuenta.entity.Transferencia;
import com.bcra.accounts.ws.cuenta.exception.TransferenciaRechazadaException;
import com.bcra.accounts.ws.cuenta.repository.LimiteCanalRepository;

import lombok.RequiredArgsConstructor;

/** Límites por titular, producto, moneda y canal; las preparaciones vigentes consumen disponibilidad. */
@Service
@RequiredArgsConstructor
public class LimiteService {

    private static final String CANAL = "WHATSAPP";

    private final LimiteCanalRepository repository;

    /** Rechaza si no hay política para el producto o se supera algún acumulado. */
    public boolean requiereDobleConfirmacion(Cuenta origen, BigDecimal monto, List<Transferencia> historial,
            Instant ahora, ZoneId zona) {
        LimiteCanal limite = repository.findByProductoAndCanal(origen.getTipo().name(), CANAL)
                .filter(valor -> valor.getMoneda() == origen.getMoneda())
                .orElseThrow(() -> new TransferenciaRechazadaException("No hay límites habilitados para el producto"));
        Instant inicioDia = ahora.atZone(zona).toLocalDate().atStartOfDay(zona).toInstant();
        Instant inicioMes = ahora.atZone(zona).toLocalDate().withDayOfMonth(1).atStartOfDay(zona).toInstant();
        List<Transferencia> operaciones = historial.stream()
                .filter(transferencia -> transferencia.getCuentaOrigen().getTipo() == origen.getTipo())
                .filter(transferencia -> transferencia.getMoneda() == origen.getMoneda())
                .filter(transferencia -> cuentaParaLimite(transferencia, ahora)).toList();
        if (acumulado(operaciones, inicioDia).add(monto).compareTo(limite.getLimiteDiario()) > 0) {
            throw new TransferenciaRechazadaException("Límite diario superado");
        }
        if (acumulado(operaciones, inicioMes).add(monto).compareTo(limite.getLimiteMensual()) > 0) {
            throw new TransferenciaRechazadaException("Límite mensual superado");
        }
        return monto.compareTo(limite.getUmbralDobleConfirmacion()) > 0;
    }

    /** Disponibilidad comprometida que todavía no se ha debitado de la cuenta. */
    public BigDecimal reservado(Cuenta origen, List<Transferencia> historial, Instant ahora) {
        return historial.stream()
                .filter(transferencia -> transferencia.getCuentaOrigen().getId().equals(origen.getId()))
                .filter(transferencia -> esReservaVigente(transferencia, ahora))
                .map(Transferencia::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean cuentaParaLimite(Transferencia transferencia, Instant ahora) {
        return transferencia.getEstado() == EstadoTransferencia.EJECUTADA
                || esReservaVigente(transferencia, ahora);
    }

    private boolean esReservaVigente(Transferencia transferencia, Instant ahora) {
        return (transferencia.getEstado() == EstadoTransferencia.PREPARADA
                || transferencia.getEstado() == EstadoTransferencia.CONFIRMADA)
                && transferencia.getExpiraEn() != null && transferencia.getExpiraEn().isAfter(ahora);
    }

    private BigDecimal acumulado(List<Transferencia> operaciones, Instant desde) {
        return operaciones.stream()
                .filter(transferencia -> !fechaContable(transferencia).isBefore(desde))
                .map(Transferencia::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Instant fechaContable(Transferencia transferencia) {
        return transferencia.getFechaEjecucion() != null
                ? transferencia.getFechaEjecucion() : transferencia.getFechaCreacion();
    }
}

