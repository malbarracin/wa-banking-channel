package com.bcra.cards.ws.tarjeta.controller;

import com.bcra.cards.ws.tarjeta.dto.AvisoViajeRequest;
import com.bcra.cards.ws.tarjeta.dto.AvisoViajeResponse;
import com.bcra.cards.ws.tarjeta.service.AvisoViajeService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Registro de avisos de viaje del titular autenticado. */
@RestController
@RequestMapping("/api/v1/tarjetas")
@RequiredArgsConstructor
public class AvisoViajeController {

    private final AvisoViajeService servicio;

    /** Confirma el registro del destino y fechas con recomendaciones de seguridad. */
    @PostMapping("/{tarjetaId}/aviso-viaje")
    @ResponseStatus(HttpStatus.CREATED)
    public AvisoViajeResponse registrar(@PathVariable UUID tarjetaId,
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @Valid @RequestBody AvisoViajeRequest request) {
        return servicio.registrar(credencial, tarjetaId, request);
    }
}
