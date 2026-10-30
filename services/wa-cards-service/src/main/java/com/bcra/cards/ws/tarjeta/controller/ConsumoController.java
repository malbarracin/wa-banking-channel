package com.bcra.cards.ws.tarjeta.controller;

import com.bcra.cards.ws.tarjeta.dto.ConsumosPageResponse;
import com.bcra.cards.ws.tarjeta.service.ConsumoService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta de consumos recientes del titular autenticado. */
@RestController
@RequestMapping("/api/v1/tarjetas/{tarjetaId}/consumos")
@RequiredArgsConstructor
public class ConsumoController {

    private final ConsumoService consumoService;

    /** Devuelve una página de consumos dentro de los límites configurados por el banco. */
    @GetMapping
    public ConsumosPageResponse listar(@PathVariable UUID tarjetaId,
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @RequestParam(required = false) Integer dias, @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return consumoService.listar(tarjetaId, credencial, dias, limit, cursor);
    }
}
