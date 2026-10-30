package com.bcra.cards.ws.tarjeta.controller;

import com.bcra.cards.ws.tarjeta.dto.BloqueoDefinitivoRequest;
import com.bcra.cards.ws.tarjeta.dto.BloqueoDefinitivoResponse;
import com.bcra.cards.ws.tarjeta.dto.BloqueoTemporalRequest;
import com.bcra.cards.ws.tarjeta.dto.BloqueoTemporalResponse;
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

/** Operaciones de bloqueo de las tarjetas del titular autenticado. */
@RestController
@RequestMapping("/api/v1/tarjetas")
@RequiredArgsConstructor
public class BloqueoController {

    private final BloqueoService bloqueoService;

    /** Bloquea definitivamente tras recibir las dos confirmaciones del cliente. */
    @PostMapping("/{tarjetaId}/bloquear/definitivo")
    public BloqueoDefinitivoResponse bloquearDefinitivo(@PathVariable UUID tarjetaId,
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @Valid @RequestBody BloqueoDefinitivoRequest solicitud) {
        return bloqueoService.bloquearDefinitivo(tarjetaId, credencial, solicitud);
    }

    /** Bloquea temporalmente tras recibir confirmación explícita. */
    @PostMapping("/{tarjetaId}/bloquear/temporal")
    public BloqueoTemporalResponse bloquearTemporal(@PathVariable UUID tarjetaId,
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @Valid @RequestBody BloqueoTemporalRequest request) {
        return bloqueoService.bloquearTemporal(credencial, tarjetaId, request);
    }

    /** Reactiva una tarjeta con bloqueo temporal tras la validación requerida. */
    @PostMapping("/{tarjetaId}/desbloquear/temporal")
    public TarjetaResponse desbloquearTemporal(@PathVariable UUID tarjetaId,
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @Valid @RequestBody DesbloqueoRequest solicitud) {
        return bloqueoService.desbloquearTemporal(tarjetaId, credencial, solicitud);
    }
}
