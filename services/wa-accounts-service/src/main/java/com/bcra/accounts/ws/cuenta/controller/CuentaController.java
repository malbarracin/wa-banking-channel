package com.bcra.accounts.ws.cuenta.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.accounts.ws.cuenta.dto.CuentaResponse;
import com.bcra.accounts.ws.cuenta.dto.SaldoResponse;
import com.bcra.accounts.ws.cuenta.service.CuentaService;

import lombok.RequiredArgsConstructor;

/** API de consulta de cuentas autenticada mediante credencial de sesión de H2. */
@RestController
@RequestMapping("/api/v1/cuentas")
@RequiredArgsConstructor
public class CuentaController {

    private final CuentaService cuentaService;

    /** Devuelve las cuentas del titular o 401 si falta una credencial válida. */
    @GetMapping
    public List<CuentaResponse> listar(
            @RequestHeader(value = "X-Credencial-Sesion", defaultValue = "") String credencial) {
        return cuentaService.listar(credencial);
    }

    /** Devuelve los saldos de la cuenta propia indicada, o 404 si no pertenece al titular. */
    @GetMapping("/{numeroCuenta}/saldo")
    public SaldoResponse consultarSaldo(
            @RequestHeader(value = "X-Credencial-Sesion", defaultValue = "") String credencial,
            @PathVariable String numeroCuenta) {
        return cuentaService.consultarSaldo(credencial, numeroCuenta);
    }
}
