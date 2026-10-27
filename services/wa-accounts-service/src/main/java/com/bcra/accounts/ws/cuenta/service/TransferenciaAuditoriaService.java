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

    /** La confirmación exitosa se confirma o revierte junto con el estado y los fondos. */
    @Transactional
    public void registrarConfirmacion(ValidarCredencialResponse titular, UUID id, AuditoriaAccion accion) {
        registrar(titular, id, AuditoriaResultado.EXITOSO, accion);
    }

    /** Registra el intento aun cuando la transacción de preparación se revierta. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(ValidarCredencialResponse titular, UUID transferenciaId, AuditoriaResultado resultado) {
        registrar(titular, transferenciaId, resultado, AuditoriaAccion.PREPARAR_TRANSFERENCIA);
    }

    /** Registra confirmaciones y rechazos independientemente de la transacción financiera. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(ValidarCredencialResponse titular, UUID transferenciaId, AuditoriaResultado resultado,
            AuditoriaAccion accion) {
        AuditoriaCuenta auditoria = new AuditoriaCuenta();
        auditoria.setAccion(accion);
        auditoria.setResultado(resultado);
        auditoria.setDocumentoTipo(titular.documentoTipo());
        auditoria.setDocumentoNumero(titular.documentoNumero());
        auditoria.setNumeroWhatsapp(titular.numeroWhatsapp());
        auditoria.setTransferenciaId(transferenciaId);
        repository.save(auditoria);
    }
}

