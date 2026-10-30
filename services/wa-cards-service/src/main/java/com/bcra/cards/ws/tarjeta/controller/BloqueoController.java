package com.bcra.cards.ws.tarjeta.controller;

import com.bcra.cards.ws.tarjeta.dto.DesbloqueoRequest;
import com.bcra.cards.ws.tarjeta.dto.TarjetaResponse;
import com.bcra.cards.ws.tarjeta.service.BloqueoService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operaciones de bloqueo de tarjetas del titular autenticado. */
@RestController
@RequestMapping("/api/v1/tarjetas/{tarjetaId}")
@RequiredArgsConstructor
public class BloqueoController {

    private final BloqueoService bloqueoService;

    /** Reactiva una tarjeta con bloqueo temporal tras la validación requerida. */
    @PostMapping("/desbloquear/temporal")
    public TarjetaResponse desbloquearTemporal(@PathVariable UUID tarjetaId,
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @Valid @RequestBody DesbloqueoRequest solicitud) {
        return bloqueoService.desbloquearTemporal(tarjetaId, credencial, solicitud);
    }
}
