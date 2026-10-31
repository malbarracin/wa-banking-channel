package com.bcra.accounts.ws.cuenta.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.accounts.ws.cuenta.client.dto.ValidarCredencialResponse;
import com.bcra.accounts.ws.cuenta.entity.AuditoriaAccion;
import com.bcra.accounts.ws.cuenta.entity.AuditoriaCuenta;
import com.bcra.accounts.ws.cuenta.entity.AuditoriaResultado;
import com.bcra.accounts.ws.cuenta.repository.AuditoriaCuentaRepository;

import lombok.RequiredArgsConstructor;

/** Auditoría de consultas autenticadas, independiente de la transacción de lectura. */
@Service
@RequiredArgsConstructor
public class MovimientoAuditoriaService {

    private final AuditoriaCuentaRepository repository;

    /** Registra el resultado sin persistir la credencial ni filtros del cliente. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(ValidarCredencialResponse identidad, UUID cuentaId, AuditoriaResultado resultado) {
        AuditoriaCuenta auditoria = new AuditoriaCuenta();
        auditoria.setAccion(AuditoriaAccion.LISTAR_MOVIMIENTOS);
        auditoria.setResultado(resultado);
        auditoria.setDocumentoTipo(identidad.documentoTipo());
        auditoria.setDocumentoNumero(identidad.documentoNumero());
        auditoria.setNumeroWhatsapp(identidad.numeroWhatsapp());
        auditoria.setCuentaId(cuentaId);
        repository.save(auditoria);
    }
}
