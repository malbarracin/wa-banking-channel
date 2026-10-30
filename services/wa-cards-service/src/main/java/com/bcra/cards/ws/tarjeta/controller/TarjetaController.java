package com.bcra.cards.ws.tarjeta.controller;

import com.bcra.cards.ws.tarjeta.dto.TarjetaResponse;
import com.bcra.cards.ws.tarjeta.service.TarjetaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API de tarjetas del titular autenticado. */
@Tag(name = "Tarjetas", description = "Consultas y gestión de tarjetas del titular autenticado")
@RestController
@RequestMapping("/api/v1/tarjetas")
@RequiredArgsConstructor
public class TarjetaController {

    private final TarjetaService tarjetaService;

    /** Lista las tarjetas con la credencial de sesión del canal. */
    @Operation(operationId = "listar", summary = "Listar tarjetas",
            description = "Devuelve únicamente tarjetas del titular; una lista vacía es un resultado válido.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operación exitosa", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/400"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/401"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/403"),
        @ApiResponse(responseCode = "500", ref = "#/components/responses/500")
    })
    @GetMapping
    public List<TarjetaResponse> listar(
            @Parameter(description = "Credencial JWT de sesión validada con H2; requiere vínculo activo",
                    example = "jwt-de-sesion", required = true)
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial) {
        return tarjetaService.listar(credencial);
    }
}
