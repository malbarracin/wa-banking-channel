package com.bcra.accounts.ws.cuenta.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.accounts.ws.cuenta.client.CredencialesServiceClient;
import com.bcra.accounts.ws.cuenta.client.dto.ValidarCredencialResponse;
import com.bcra.accounts.ws.cuenta.dto.CuentaResponse;
import com.bcra.accounts.ws.cuenta.dto.SaldoResponse;
import com.bcra.accounts.ws.cuenta.entity.Cuenta;
import com.bcra.accounts.ws.cuenta.exception.CuentaNoEncontradaException;
import com.bcra.accounts.ws.cuenta.mapper.CuentaMapper;
import com.bcra.accounts.ws.cuenta.repository.CuentaRepository;

import lombok.RequiredArgsConstructor;

/** Consulta productos únicamente después de validar la sesión con H2 en cada operación. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CuentaService {

    private final CredencialesServiceClient credencialesServiceClient;
    private final CuentaRepository cuentaRepository;
    private final CuentaMapper cuentaMapper;
    private final AuditoriaCuentaService auditoriaCuentaService;

    /** Devuelve exclusivamente cuentas del documento confirmado por H2. */
    public List<CuentaResponse> listar(String credencial) {
        ValidarCredencialResponse titular = credencialesServiceClient.validar(credencial);
        List<CuentaResponse> cuentas = cuentaRepository.findByTitularDocumentoTipoAndTitularDocumentoNumero(
                titular.documentoTipo(), titular.documentoNumero()).stream().map(cuentaMapper::toResponse).toList();
        auditoriaCuentaService.registrarListado(titular);
        return cuentas;
    }

    /** Consulta ambos saldos; una cuenta ajena produce el mismo 404 que una inexistente. */
    public SaldoResponse consultarSaldo(String credencial, String numeroCuenta) {
        ValidarCredencialResponse titular = credencialesServiceClient.validar(credencial);
        Cuenta cuenta = cuentaRepository.findByNumeroCuentaAndTitularDocumentoTipoAndTitularDocumentoNumero(
                numeroCuenta, titular.documentoTipo(), titular.documentoNumero()).orElseThrow(() -> {
                    auditoriaCuentaService.registrarSaldoRechazado(titular);
                    return new CuentaNoEncontradaException();
                });
        SaldoResponse saldo = cuentaMapper.toSaldoResponse(cuenta);
        auditoriaCuentaService.registrarSaldo(titular, cuenta.getId());
        return saldo;
    }
}
