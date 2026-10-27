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

/** Conserva la auditoría del titular validado aunque se rechace la consulta. */
@Service
@RequiredArgsConstructor
public class AuditoriaCuentaService {

    private final AuditoriaCuentaRepository auditoriaCuentaRepository;

    /** Registra una consulta de listado; no hay una cuenta individual asociada. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarListado(ValidarCredencialResponse titular) {
        AuditoriaCuenta auditoria = crear(titular, AuditoriaAccion.LISTAR_CUENTAS, AuditoriaResultado.EXITOSO);
        auditoriaCuentaRepository.save(auditoria);
    }

    /** Registra el saldo consultado sin persistir los importes en la auditoría. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarSaldo(ValidarCredencialResponse titular, UUID cuentaId) {
        AuditoriaCuenta auditoria = crear(titular, AuditoriaAccion.CONSULTAR_SALDO, AuditoriaResultado.EXITOSO);
        auditoria.setCuentaId(cuentaId);
        auditoriaCuentaRepository.save(auditoria);
    }

    /** Registra el rechazo sin asociar ni revelar una cuenta ajena. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarSaldoRechazado(ValidarCredencialResponse titular) {
        AuditoriaCuenta auditoria = crear(titular, AuditoriaAccion.CONSULTAR_SALDO, AuditoriaResultado.RECHAZADO);
        auditoria.setMotivo("Cuenta no encontrada");
        auditoriaCuentaRepository.save(auditoria);
    }

    private AuditoriaCuenta crear(ValidarCredencialResponse titular, AuditoriaAccion accion,
            AuditoriaResultado resultado) {
        AuditoriaCuenta auditoria = new AuditoriaCuenta();
        auditoria.setAccion(accion);
        auditoria.setResultado(resultado);
        auditoria.setNumeroWhatsapp(titular.numeroWhatsapp());
        auditoria.setDocumentoTipo(titular.documentoTipo());
        auditoria.setDocumentoNumero(titular.documentoNumero());
        return auditoria;
    }
}
