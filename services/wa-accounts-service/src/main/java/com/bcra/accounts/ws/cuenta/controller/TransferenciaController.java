package com.bcra.accounts.ws.cuenta.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.accounts.ws.cuenta.dto.TransferenciaPreparadaResponse;
import com.bcra.accounts.ws.cuenta.dto.TransferenciaRequest;
import com.bcra.accounts.ws.cuenta.service.TransferenciaService;

import lombok.RequiredArgsConstructor;

/** API de preparación de transferencias; la confirmación es una operación posterior. */
@RestController
@RequestMapping("/api/v1/transferencias")
@RequiredArgsConstructor
public class TransferenciaController {

    private final TransferenciaService service;

    /** Valida sesión y reglas operativas; devuelve 201 con resumen y vencimiento sin efectuar débito. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferenciaPreparadaResponse preparar(
            @RequestHeader(value = "X-Credencial-Sesion", defaultValue = "") String credencial,
            @Valid @RequestBody TransferenciaRequest solicitud) {
        return service.preparar(credencial, solicitud);
    }
}

