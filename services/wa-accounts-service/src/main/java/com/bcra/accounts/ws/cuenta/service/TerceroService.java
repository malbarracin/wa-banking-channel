package com.bcra.accounts.ws.cuenta.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.accounts.ws.cuenta.client.CredencialesServiceClient;
import com.bcra.accounts.ws.cuenta.client.dto.ValidarCredencialResponse;
import com.bcra.accounts.ws.cuenta.dto.TerceroResponse;
import com.bcra.accounts.ws.cuenta.mapper.TerceroMapper;
import com.bcra.accounts.ws.cuenta.repository.TerceroAgendadoRepository;

import lombok.RequiredArgsConstructor;

/** Consulta la agenda exclusivamente para la identidad validada por H2. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TerceroService {

    private final CredencialesServiceClient credencialesServiceClient;
    private final TerceroAgendadoRepository terceroAgendadoRepository;
    private final TerceroMapper terceroMapper;
    private final AuditoriaCuentaService auditoriaCuentaService;

    /** Revalida la sesión y devuelve los terceros activos del titular, o una lista vacía. */
    public List<TerceroResponse> listar(String credencial) {
        ValidarCredencialResponse titular = credencialesServiceClient.validar(credencial);
        List<TerceroResponse> terceros = terceroAgendadoRepository
                .findByTitularDocumentoTipoAndTitularDocumentoNumeroAndActivoTrue(
                        titular.documentoTipo(), titular.documentoNumero())
                .stream()
                .map(terceroMapper::toResponse)
                .toList();
        auditoriaCuentaService.registrarListadoTerceros(titular);
        return terceros;
    }
}
