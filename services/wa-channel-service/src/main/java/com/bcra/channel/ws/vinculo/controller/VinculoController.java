package com.bcra.channel.ws.vinculo.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.channel.ws.vinculo.dto.AceptarTerminosRequest;
import com.bcra.channel.ws.vinculo.dto.VinculoResponse;
import com.bcra.channel.ws.vinculo.service.VinculoService;
import com.bcra.channel.ws.vinculo.service.VerificacionService;
import com.bcra.channel.ws.vinculo.dto.VerificarIdentidadRequest;
import com.bcra.channel.ws.vinculo.dto.VerificacionResultadoResponse;

import lombok.RequiredArgsConstructor;

/** API del vínculo al canal WhatsApp. */
@RestController
@RequestMapping("/api/v1/vinculos")
@RequiredArgsConstructor
public class VinculoController {

    private final VinculoService vinculoService;
    private final VerificacionService verificacionService;

    /**
     * Verifica identidad después de aceptar términos.
     * @param numero número E.164
     * @param request pruebas de identidad
     * @return resultado de la verificación, sin credencial de sesión
     */
    @PostMapping("/{numero}/verificaciones")
    public VerificacionResultadoResponse verificar(@PathVariable String numero,
            @Valid @RequestBody VerificarIdentidadRequest request) {
        return verificacionService.verificar(numero, request);
    }

    /**
     * Confirma la aceptación explícita de los términos del canal.
     * @param request número de WhatsApp, sin datos de identificación
     * @return 201 con el vínculo pendiente de verificación
     */
    @PostMapping("/terminos")
    public ResponseEntity<VinculoResponse> aceptarTerminos(@Valid @RequestBody AceptarTerminosRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vinculoService.aceptarTerminos(request.numeroWhatsapp()));
    }
}
