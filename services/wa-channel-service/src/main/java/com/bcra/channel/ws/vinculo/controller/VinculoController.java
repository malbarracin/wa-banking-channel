package com.bcra.channel.ws.vinculo.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.channel.ws.vinculo.dto.AceptarTerminosRequest;
import com.bcra.channel.ws.vinculo.dto.VinculoResponse;
import com.bcra.channel.ws.vinculo.dto.HistorialVinculoResponse;
import com.bcra.channel.ws.vinculo.service.VinculoService;

import lombok.RequiredArgsConstructor;

/** API del vínculo al canal WhatsApp. */
@RestController
@RequestMapping("/api/v1/vinculos")
@RequiredArgsConstructor
public class VinculoController {

    private final VinculoService vinculoService;

    /**
     * Devuelve el historial resumido para uso interno de soporte del banco.
     * @param numero número de WhatsApp consultado
     * @return 200 con estado actual y eventos descendentes; 404 si no hay vínculo registrado
     */
    @GetMapping("/{numero}/historial")
    public HistorialVinculoResponse consultarHistorial(@PathVariable String numero) {
        return vinculoService.consultarHistorial(numero);
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
