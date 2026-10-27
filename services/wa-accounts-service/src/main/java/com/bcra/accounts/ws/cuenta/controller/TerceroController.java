package com.bcra.accounts.ws.cuenta.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.accounts.ws.cuenta.dto.TerceroResponse;
import com.bcra.accounts.ws.cuenta.service.TerceroService;

import lombok.RequiredArgsConstructor;

/** API de consulta de destinatarios agendados por el titular autenticado. */
@RestController
@RequestMapping("/api/v1/terceros")
@RequiredArgsConstructor
public class TerceroController {

    private final TerceroService terceroService;

    /** Devuelve los terceros activos del titular o 401 si falta una credencial válida. */
    @GetMapping
    public List<TerceroResponse> listar(
            @RequestHeader(value = "X-Credencial-Sesion", defaultValue = "") String credencial) {
        return terceroService.listar(credencial);
    }
}
