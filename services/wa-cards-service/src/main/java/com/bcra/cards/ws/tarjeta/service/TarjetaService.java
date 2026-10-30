package com.bcra.cards.ws.tarjeta.service;

import com.bcra.cards.ws.exception.UnauthorizedException;
import com.bcra.cards.ws.tarjeta.client.CredencialesServiceClient;
import com.bcra.cards.ws.tarjeta.dto.TarjetaResponse;
import com.bcra.cards.ws.tarjeta.mapper.TarjetaMapper;
import com.bcra.cards.ws.tarjeta.repository.TarjetaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Consulta tarjetas exclusivamente para la identidad validada por H2. */
@Service
@RequiredArgsConstructor
public class TarjetaService {

    private final CredencialesServiceClient credencialesClient;
    private final TarjetaRepository tarjetaRepository;
    private final TarjetaMapper tarjetaMapper;
    private final AuditoriaTarjetaService auditoriaService;

    /** Valida cada solicitud antes de consultar productos y audita el listado. */
    public List<TarjetaResponse> listar(String credencial) {
        if (credencial == null || credencial.isBlank()) {
            throw new UnauthorizedException("Credencial no válida");
        }
        var identidad = credencialesClient.validar(credencial);
        var tarjetas = tarjetaRepository.findByTitularDocumentoTipoAndTitularDocumentoNumero(
                identidad.documentoTipo(), identidad.documentoNumero()).stream()
                .map(tarjetaMapper::toResponse).toList();
        auditoriaService.registrarListado(identidad);
        return tarjetas;
    }
}
