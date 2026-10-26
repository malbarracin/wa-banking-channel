package com.bcra.credentials.ws.credencial.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.credentials.ws.credencial.dto.EventoCredencialResponse;
import com.bcra.credentials.ws.credencial.dto.HistorialCredencialResponse;
import com.bcra.credentials.ws.credencial.entity.CredencialEstado;
import com.bcra.credentials.ws.credencial.entity.CredencialSesion;
import com.bcra.credentials.ws.credencial.exception.HistorialNoEncontradoException;
import com.bcra.credentials.ws.credencial.mapper.CredencialMapper;
import com.bcra.credentials.ws.credencial.repository.CredencialRepository;

import lombok.RequiredArgsConstructor;

/** Consulta de soporte sin modificar el estado persistido ni generar nuevos eventos. */
@Service
@RequiredArgsConstructor
public class HistorialCredencialService {

    private final CredencialRepository repository;
    private final CredencialAuditService auditService;
    private final CredencialMapper mapper;

    /**
     * @param numero número WhatsApp E.164
     * @return historial inmodificable; 404 si falta la credencial o no existen eventos
     */
    @Transactional(readOnly = true)
    public HistorialCredencialResponse consultar(String numero) {
        List<EventoCredencialResponse> eventos = auditService.consultarHistorial(numero).stream()
                .map(mapper::toEventoResponse).toList();
        if (eventos.isEmpty()) {
            throw new HistorialNoEncontradoException();
        }
        CredencialSesion ultima = repository.findFirstByNumeroWhatsappOrderByFechaEmisionDesc(numero)
                .orElseThrow(HistorialNoEncontradoException::new);
        CredencialEstado estado = ultima.getEstado();
        if (estado == CredencialEstado.ACTIVA && !ultima.getFechaExpiracion().isAfter(Instant.now())) {
            estado = CredencialEstado.EXPIRADA;
        }
        return mapper.toHistorialResponse(ultima, estado, eventos);
    }
}
