package com.bcra.cards.ws.tarjeta.service;

import com.bcra.cards.ws.exception.UnauthorizedException;
import com.bcra.cards.ws.tarjeta.client.CredencialesServiceClient;
import com.bcra.cards.ws.tarjeta.dto.BloqueoTemporalRequest;
import com.bcra.cards.ws.tarjeta.dto.BloqueoTemporalResponse;
import com.bcra.cards.ws.tarjeta.dto.DesbloqueoRequest;
import com.bcra.cards.ws.tarjeta.dto.TarjetaResponse;
import com.bcra.cards.ws.tarjeta.entity.Bloqueo;
import com.bcra.cards.ws.tarjeta.entity.EstadoTarjeta;
import com.bcra.cards.ws.tarjeta.entity.TipoBloqueo;
import com.bcra.cards.ws.tarjeta.exception.BloqueoDefinitivoIrreversibleException;
import com.bcra.cards.ws.tarjeta.exception.BloqueoNoAplicableException;
import com.bcra.cards.ws.tarjeta.exception.SolicitudBloqueoInvalidaException;
import com.bcra.cards.ws.tarjeta.exception.TarjetaNoEncontradaException;
import com.bcra.cards.ws.tarjeta.mapper.BloqueoMapper;
import com.bcra.cards.ws.tarjeta.mapper.TarjetaMapper;
import com.bcra.cards.ws.tarjeta.repository.BloqueoRepository;
import com.bcra.cards.ws.tarjeta.repository.TarjetaRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persiste el estado y el bloqueo en una transacción y solicita su auditoría independiente. */
@Service
@RequiredArgsConstructor
public class BloqueoService {

    private final CredencialesServiceClient credencialesClient;
    private final TarjetaRepository tarjetaRepository;
    private final BloqueoRepository bloqueoRepository;
    private final AuditoriaTarjetaService auditoriaService;
    private final BloqueoMapper bloqueoMapper;

    private final TarjetaMapper mapper;

    @Value("${cards.bloqueo.validacion-adicional-requerida:true}")
    private boolean validacionAdicionalRequerida;

    /** Valida autorización y confirmación antes de modificar la tarjeta. */
    @Transactional
    public BloqueoTemporalResponse bloquearTemporal(String credencial, UUID tarjetaId,
            BloqueoTemporalRequest request) {
        if (credencial == null || credencial.isBlank()) {
            throw new UnauthorizedException("Credencial no válida");
        }
        var identidad = credencialesClient.validar(credencial);
        if (request == null || !tarjetaId.equals(request.tarjetaId())
                || !"CONFIRMAR".equals(request.confirmar())) {
            throw new SolicitudBloqueoInvalidaException();
        }
        var tarjeta = tarjetaRepository.buscarParaBloqueo(tarjetaId,
                identidad.documentoTipo(), identidad.documentoNumero())
                .orElseThrow(TarjetaNoEncontradaException::new);
        if (!tarjeta.isActiva() || tarjeta.getEstado() != EstadoTarjeta.ACTIVA) {
            throw new BloqueoNoAplicableException();
        }
        var fecha = Instant.now();
        tarjeta.setEstado(EstadoTarjeta.BLOQUEO_TEMPORAL);
        tarjetaRepository.save(tarjeta);
        var bloqueo = new Bloqueo();
        bloqueo.setTarjetaId(tarjetaId);
        bloqueo.setTipo(TipoBloqueo.TEMPORAL);
        bloqueo.setFechaBloqueo(fecha);
        bloqueo.setUsuarioEjecutor(identidad.numeroWhatsapp());
        bloqueoRepository.save(bloqueo);
        tarjetaRepository.flush();
        bloqueoRepository.flush();
        auditoriaService.registrarBloqueoTemporal(identidad, tarjetaId);
        return bloqueoMapper.toResponse(tarjeta);
    }

    /** Desbloquea únicamente estados temporales y confirma la operación con auditoría atómica. */
    @Transactional
    public TarjetaResponse desbloquearTemporal(UUID tarjetaId, String credencial, DesbloqueoRequest solicitud) {
        if (credencial == null || credencial.isBlank()) {
            throw new UnauthorizedException("Credencial no válida");
        }
        var titular = credencialesClient.validar(credencial);
        if (!tarjetaId.equals(solicitud.tarjetaId())) {
            throw new IllegalArgumentException("La tarjeta del cuerpo debe coincidir con la ruta");
        }
        var tarjeta = tarjetaRepository.buscarParaActualizar(tarjetaId, titular.documentoTipo(), titular.documentoNumero())
                .orElseThrow(TarjetaNoEncontradaException::new);
        if (tarjeta.getEstado() == EstadoTarjeta.BLOQUEO_DEFINITIVO) {
            throw new BloqueoDefinitivoIrreversibleException();
        }
        if (tarjeta.getEstado() != EstadoTarjeta.BLOQUEO_TEMPORAL) {
            throw new BloqueoNoAplicableException();
        }
        if (validacionAdicionalRequerida && !Boolean.TRUE.equals(solicitud.validacionAdicional())) {
            throw new IllegalArgumentException("Se requiere validación adicional");
        }
        Instant fecha = Instant.now();
        bloqueoRepository.findByTarjetaIdOrderByFechaBloqueoDesc(tarjetaId).stream()
                .filter(bloqueo -> bloqueo.getTipo() == TipoBloqueo.TEMPORAL)
                .filter(bloqueo -> bloqueo.getFechaDesbloqueo() == null)
                .forEach(bloqueo -> bloqueo.setFechaDesbloqueo(fecha));
        tarjeta.setEstado(EstadoTarjeta.ACTIVA);
        tarjeta.setActiva(true);
        auditoriaService.registrarDesbloqueo(titular, tarjetaId);
        return mapper.toResponse(tarjeta);
    }
}
