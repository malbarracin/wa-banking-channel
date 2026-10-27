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

/** Conserva intentos autenticados sin credenciales, montos ni destinatarios en la auditoría. */
@Service
@RequiredArgsConstructor
public class TransferenciaAuditoriaService {

    private final AuditoriaCuentaRepository repository;

    /** Registra el intento aun cuando la transacción de preparación se revierta. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(ValidarCredencialResponse titular, UUID transferenciaId, AuditoriaResultado resultado) {
        AuditoriaCuenta auditoria = new AuditoriaCuenta();
        auditoria.setAccion(AuditoriaAccion.PREPARAR_TRANSFERENCIA);
        auditoria.setResultado(resultado);
        auditoria.setDocumentoTipo(titular.documentoTipo());
        auditoria.setDocumentoNumero(titular.documentoNumero());
        auditoria.setNumeroWhatsapp(titular.numeroWhatsapp());
        auditoria.setTransferenciaId(transferenciaId);
        repository.save(auditoria);
    }
}

