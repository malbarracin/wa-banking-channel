package com.bcra.cards.ws.tarjeta.controller;

import com.bcra.cards.ws.tarjeta.dto.TarjetaResponse;
import com.bcra.cards.ws.tarjeta.service.TarjetaService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API de tarjetas del titular autenticado. */
@RestController
@RequestMapping("/api/v1/tarjetas")
@RequiredArgsConstructor
public class TarjetaController {

    private final TarjetaService tarjetaService;

    /** Lista las tarjetas con la credencial de sesión del canal. */
    @GetMapping
    public List<TarjetaResponse> listar(
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial) {
        return tarjetaService.listar(credencial);
    }
}
