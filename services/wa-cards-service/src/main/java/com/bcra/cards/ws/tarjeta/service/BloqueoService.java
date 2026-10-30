package com.bcra.cards.ws.tarjeta.service;

import com.bcra.cards.ws.exception.UnauthorizedException;
import com.bcra.cards.ws.tarjeta.client.CredencialesServiceClient;
import com.bcra.cards.ws.tarjeta.dto.DesbloqueoRequest;
import com.bcra.cards.ws.tarjeta.dto.TarjetaResponse;
import com.bcra.cards.ws.tarjeta.entity.EstadoTarjeta;
import com.bcra.cards.ws.tarjeta.entity.TipoBloqueo;
import com.bcra.cards.ws.tarjeta.exception.BloqueoDefinitivoIrreversibleException;
import com.bcra.cards.ws.tarjeta.exception.BloqueoNoAplicableException;
import com.bcra.cards.ws.tarjeta.exception.TarjetaNoEncontradaException;
import com.bcra.cards.ws.tarjeta.mapper.TarjetaMapper;
import com.bcra.cards.ws.tarjeta.repository.BloqueoRepository;
import com.bcra.cards.ws.tarjeta.repository.TarjetaRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Autoriza al titular y aplica las transiciones permitidas de bloqueo. */
@Service
@RequiredArgsConstructor
public class BloqueoService {

    private final CredencialesServiceClient credenciales;
    private final TarjetaRepository tarjetas;
    private final BloqueoRepository bloqueos;
    private final TarjetaMapper mapper;
    private final AuditoriaTarjetaService auditoria;

    @Value("${cards.bloqueo.validacion-adicional-requerida:true}")
    private boolean validacionAdicionalRequerida;

    /** Desbloquea únicamente estados temporales y confirma la operación con auditoría atómica. */
    @Transactional
    public TarjetaResponse desbloquearTemporal(UUID tarjetaId, String credencial, DesbloqueoRequest solicitud) {
        if (credencial == null || credencial.isBlank()) {
            throw new UnauthorizedException("Credencial no válida");
        }
        var titular = credenciales.validar(credencial);
        if (!tarjetaId.equals(solicitud.tarjetaId())) {
            throw new IllegalArgumentException("La tarjeta del cuerpo debe coincidir con la ruta");
        }
        var tarjeta = tarjetas.buscarParaActualizar(tarjetaId, titular.documentoTipo(), titular.documentoNumero())
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
        bloqueos.findByTarjetaIdOrderByFechaBloqueoDesc(tarjetaId).stream()
                .filter(bloqueo -> bloqueo.getTipo() == TipoBloqueo.TEMPORAL)
                .filter(bloqueo -> bloqueo.getFechaDesbloqueo() == null)
                .forEach(bloqueo -> bloqueo.setFechaDesbloqueo(fecha));
        tarjeta.setEstado(EstadoTarjeta.ACTIVA);
        tarjeta.setActiva(true);
        auditoria.registrarDesbloqueo(titular, tarjetaId);
        return mapper.toResponse(tarjeta);
    }
}
