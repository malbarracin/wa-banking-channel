package com.bcra.accounts.ws.cuenta.controller;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.accounts.ws.cuenta.dto.MovimientosPageResponse;
import com.bcra.accounts.ws.cuenta.entity.TipoMovimiento;
import com.bcra.accounts.ws.cuenta.service.MovimientoService;

import lombok.RequiredArgsConstructor;

/** Consultas paginadas de movimientos del titular autenticado. */
@RestController
@RequestMapping("/api/v1/cuentas/{numero}/movimientos")
@RequiredArgsConstructor
public class MovimientoController {

    private final MovimientoService service;

    /** Devuelve movimientos de la cuenta; fechas ISO-8601 UTC y cursor de la respuesta anterior. */
    @GetMapping
    public MovimientosPageResponse listar(
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @PathVariable String numero,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
            @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return service.listar(credencial, numero, desde, hasta, tipo, limit, cursor);
    }
}
