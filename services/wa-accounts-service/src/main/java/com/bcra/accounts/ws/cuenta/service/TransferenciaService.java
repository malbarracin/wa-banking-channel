package com.bcra.accounts.ws.cuenta.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.accounts.ws.cuenta.client.CredencialesServiceClient;
import com.bcra.accounts.ws.cuenta.client.dto.ValidarCredencialResponse;
import com.bcra.accounts.ws.cuenta.dto.TransferenciaPreparadaResponse;
import com.bcra.accounts.ws.cuenta.dto.TransferenciaRequest;
import com.bcra.accounts.ws.cuenta.entity.AuditoriaResultado;
import com.bcra.accounts.ws.cuenta.entity.Cuenta;
import com.bcra.accounts.ws.cuenta.entity.EstadoTransferencia;
import com.bcra.accounts.ws.cuenta.entity.TerceroAgendado;
import com.bcra.accounts.ws.cuenta.entity.Transferencia;
import com.bcra.accounts.ws.cuenta.exception.CuentaNoEncontradaException;
import com.bcra.accounts.ws.cuenta.exception.TransferenciaRechazadaException;
import com.bcra.accounts.ws.cuenta.mapper.TransferenciaMapper;
import com.bcra.accounts.ws.cuenta.repository.CuentaRepository;
import com.bcra.accounts.ws.cuenta.repository.TerceroAgendadoRepository;
import com.bcra.accounts.ws.cuenta.repository.TransferenciaRepository;

import lombok.RequiredArgsConstructor;

/** Prepara sin debitar; serializa validación y reservas bajo bloqueo del titular. */
@Service
@RequiredArgsConstructor
public class TransferenciaService {

    private final CredencialesServiceClient credenciales;
    private final CuentaRepository cuentas;
    private final TerceroAgendadoRepository terceros;
    private final TransferenciaRepository transferencias;
    private final HorarioService horario;
    private final LimiteService limites;
    private final TransferenciaMapper mapper;
    private final TransferenciaAuditoriaService auditoria;
    private final Clock clock;

    @Value("${limits.transferencia.expiracion-preparada-minutos:5}")
    private long expiracionMinutos;

    /** Valida la identidad con H2 en cada solicitud y devuelve la preparación persistida. */
    @Transactional
    public TransferenciaPreparadaResponse preparar(String credencial, TransferenciaRequest solicitud) {
        ValidarCredencialResponse titular = credenciales.validar(credencial);
        try {
            return prepararAutenticada(titular, solicitud);
        } catch (CuentaNoEncontradaException | TransferenciaRechazadaException | IllegalArgumentException rechazo) {
            auditoria.registrar(titular, null, AuditoriaResultado.RECHAZADO);
            throw rechazo;
        }
    }

    private TransferenciaPreparadaResponse prepararAutenticada(ValidarCredencialResponse titular,
            TransferenciaRequest solicitud) {
        if (!solicitud.isDestinoValido()) {
            throw new IllegalArgumentException("Debe indicar exactamente un destino");
        }
        List<Cuenta> propias = cuentas.bloquearDelTitular(titular.documentoTipo(), titular.documentoNumero());
        Cuenta origen = propias.stream().filter(cuenta -> cuenta.getNumeroCuenta().equals(solicitud.cuentaOrigen()))
                .findFirst().orElseThrow(CuentaNoEncontradaException::new);
        Instant ahora = clock.instant();
        horario.validar(ahora);
        if (!origen.isActiva()) {
            throw new TransferenciaRechazadaException("Cuenta origen inactiva");
        }
        if (origen.getMoneda() != solicitud.moneda()) {
            throw new TransferenciaRechazadaException("La moneda debe coincidir con la cuenta origen");
        }
        Transferencia transferencia = new Transferencia();
        transferencia.setCuentaOrigen(origen);
        validarDestino(transferencia, propias, titular, solicitud);
        Instant inicioMes = ahora.atZone(horario.zona()).toLocalDate().withDayOfMonth(1)
                .atStartOfDay(horario.zona()).toInstant();
        List<Transferencia> historial = transferencias.buscarParaLimites(
                titular.documentoTipo(), titular.documentoNumero(), inicioMes);
        boolean doble = limites.requiereDobleConfirmacion(origen, solicitud.monto(), historial, ahora, horario.zona());
        if (origen.getSaldoDisponible().subtract(limites.reservado(origen, historial, ahora))
                .compareTo(solicitud.monto()) < 0) {
            throw new TransferenciaRechazadaException("Saldo disponible insuficiente");
        }
        if (expiracionMinutos <= 0) {
            throw new IllegalStateException("Expiración de preparación inválida");
        }
        transferencia.setMonto(solicitud.monto());
        transferencia.setMoneda(solicitud.moneda());
        transferencia.setConcepto(solicitud.concepto());
        transferencia.setEstado(EstadoTransferencia.PREPARADA);
        transferencia.setDobleConfirmacionRequerida(doble);
        transferencia.setExpiraEn(ahora.plus(Duration.ofMinutes(expiracionMinutos)));
        transferencia = transferencias.saveAndFlush(transferencia);
        auditoria.registrar(titular, transferencia.getId(), AuditoriaResultado.EXITOSO);
        String resumen = "Transferir " + solicitud.monto().toPlainString() + " " + solicitud.moneda()
                + " desde " + origen.getAlias() + " a "
                + (transferencia.getCuentaDestino() != null
                        ? transferencia.getCuentaDestino().getAlias() : transferencia.getTercero().getAlias());
        return mapper.toPreparadaResponse(transferencia, resumen);
    }

    private void validarDestino(Transferencia transferencia, List<Cuenta> propias, ValidarCredencialResponse titular,
            TransferenciaRequest solicitud) {
        if (solicitud.terceroId() != null) {
            TerceroAgendado tercero = terceros.findById(solicitud.terceroId())
                    .filter(TerceroAgendado::isActivo)
                    .filter(valor -> valor.getTitularDocumentoTipo().equals(titular.documentoTipo())
                            && valor.getTitularDocumentoNumero().equals(titular.documentoNumero()))
                    .orElseThrow(() -> new TransferenciaRechazadaException(
                            "Destinatario no agendado; utilice sucursal o app"));
            if (tercero.getNumeroCuentaDestino().equals(solicitud.cuentaOrigen())) {
                throw new TransferenciaRechazadaException("La cuenta destino debe ser distinta del origen");
            }
            transferencia.setTercero(tercero);
        } else {
            Cuenta destino = propias.stream()
                    .filter(cuenta -> cuenta.getNumeroCuenta().equals(solicitud.cuentaDestino()))
                    .findFirst().orElseThrow(CuentaNoEncontradaException::new);
            if (!destino.isActiva() || destino.getId().equals(transferencia.getCuentaOrigen().getId())) {
                throw new TransferenciaRechazadaException("Cuenta destino no habilitada");
            }
            if (destino.getMoneda() != solicitud.moneda()) {
                throw new TransferenciaRechazadaException("La moneda debe coincidir con la cuenta destino");
            }
            transferencia.setCuentaDestino(destino);
        }
    }
}

