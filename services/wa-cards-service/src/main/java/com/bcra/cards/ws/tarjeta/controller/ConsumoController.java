package com.bcra.cards.ws.tarjeta.controller;

import com.bcra.cards.ws.tarjeta.dto.ConsumosPageResponse;
import com.bcra.cards.ws.tarjeta.service.ConsumoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta de consumos recientes del titular autenticado. */
@Tag(name = "Tarjetas", description = "Consultas y gestión de tarjetas del titular autenticado")
@RestController
@RequestMapping("/api/v1/tarjetas/{tarjetaId}/consumos")
@RequiredArgsConstructor
public class ConsumoController {

    private final ConsumoService consumoService;

    /** Devuelve una página de consumos dentro de los límites configurados por el banco. */
    @Operation(operationId = "listar_1", summary = "Consultar consumos recientes",
            description = "Pagina por fecha e identificador descendentes con ventana congelada; sin datos sensibles.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operación exitosa", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/400"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/401"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/403"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/404"),
        @ApiResponse(responseCode = "500", ref = "#/components/responses/500")
    })
    @GetMapping
    public ConsumosPageResponse listar(@Parameter(description = "Identificador de la tarjeta del titular",
                example = "11111111-1111-4111-8111-111111111111")
            @PathVariable UUID tarjetaId,
            @Parameter(description = "Credencial JWT de sesión validada con H2; requiere vínculo activo",
                    example = "jwt-de-sesion", required = true)
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @Parameter(description = "Ventana en días; se limita al máximo configurado (90 por defecto)",
                    example = "30")
            @RequestParam(required = false) Integer dias,
                @Parameter(description = "Cantidad por página; se limita al tope configurado (10 por defecto)",

                    example = "5")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Cursor opaco devuelto por la página anterior; omitir en la primera consulta",
                    example = "MTExMTExMTEtMTExMS00MTExLTgxMTEtMTExMTExMTExMTExfDMwfDIwMjYtMTAtM"
                        + "zBUMTI6MDA6MDBafDIwMjYtMTAtMjlUMTI6MDA6MDBafDE")
            @RequestParam(required = false) String cursor) {
        return consumoService.listar(tarjetaId, credencial, dias, limit, cursor);
    }
}
