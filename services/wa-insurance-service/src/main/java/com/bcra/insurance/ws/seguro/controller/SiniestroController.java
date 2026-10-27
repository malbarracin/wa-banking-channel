package com.bcra.insurance.ws.seguro.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.insurance.ws.seguro.dto.DenunciaRequest;
import com.bcra.insurance.ws.seguro.dto.DenunciaResponse;
import com.bcra.insurance.ws.seguro.service.SiniestroService;

import lombok.RequiredArgsConstructor;

/** Apertura de denuncias de siniestro con credencial de sesión de H2. */
@RestController
@RequestMapping("/api/v1/siniestros")
@RequiredArgsConstructor
public class SiniestroController {

    private final SiniestroService siniestros;

    /**
     * Registra una denuncia confirmada tras validar los cuatro campos mínimos.
     *
     * @param credencial JWT de sesión en {@code X-Credencial-Sesion}
     * @param request captura de póliza, tipo, fecha y descripción
     * @return caso abierto con número único y próximos pasos
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DenunciaResponse denunciar(
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @Valid @RequestBody DenunciaRequest request) {
        return siniestros.denunciar(credencial, request);
    }
}
