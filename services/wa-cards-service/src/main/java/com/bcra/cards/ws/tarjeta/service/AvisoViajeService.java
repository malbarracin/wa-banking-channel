package com.bcra.cards.ws.tarjeta.service;

import com.bcra.cards.ws.exception.UnauthorizedException;
import com.bcra.cards.ws.tarjeta.client.CredencialesServiceClient;
import com.bcra.cards.ws.tarjeta.dto.AvisoViajeRequest;
import com.bcra.cards.ws.tarjeta.dto.AvisoViajeResponse;
import com.bcra.cards.ws.tarjeta.exception.TarjetaNoEncontradaException;
import com.bcra.cards.ws.tarjeta.mapper.AvisoViajeMapper;
import com.bcra.cards.ws.tarjeta.repository.AvisoViajeRepository;
import com.bcra.cards.ws.tarjeta.repository.TarjetaRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registra avisos confirmados de las tarjetas del titular autenticado. */
@Service
@RequiredArgsConstructor
public class AvisoViajeService {

    private final CredencialesServiceClient credencialesClient;
    private final TarjetaRepository tarjetaRepository;
    private final AvisoViajeRepository avisoRepository;
    private final AvisoViajeMapper mapper;

    @Value("${cards.aviso-viaje.recomendaciones:Usá canales oficiales y no compartas tus claves.}")
    private String recomendaciones;

    /** Valida la sesión, titularidad e intervalo antes de persistir el aviso confirmado. */
    @Transactional
    public AvisoViajeResponse registrar(String credencial, UUID tarjetaId, AvisoViajeRequest request) {
        if (credencial == null || credencial.isBlank()) {
            throw new UnauthorizedException("Credencial no válida");
        }
        var identidad = credencialesClient.validar(credencial);
        if (!tarjetaId.equals(request.tarjetaId()) || request.fechaDesde() == null
                || request.fechaHasta() == null || request.fechaHasta().isBefore(request.fechaDesde())) {
            throw new IllegalArgumentException("La solicitud no es válida");
        }
        tarjetaRepository.findByIdAndTitularDocumentoTipoAndTitularDocumentoNumero(
                tarjetaId, identidad.documentoTipo(), identidad.documentoNumero())
                .orElseThrow(TarjetaNoEncontradaException::new);
        var aviso = mapper.toEntity(request);
        aviso.setFechaRegistro(Instant.now());
        avisoRepository.save(aviso);
        String mensaje = "Aviso de viaje registrado y confirmado para " + aviso.getPais()
                + (aviso.getRegion() == null ? "" : " / " + aviso.getRegion())
                + ", desde " + aviso.getFechaDesde() + " hasta " + aviso.getFechaHasta() + ". "
                + recomendaciones
                + " Siguen aplicando las políticas internacionales del emisor y los límites del banco.";
        return mapper.toResponse(aviso, mensaje);
    }
}
