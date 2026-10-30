package com.bcra.cards.ws.tarjeta.controller;

import com.bcra.cards.ws.tarjeta.dto.AvisoViajeRequest;
import com.bcra.cards.ws.tarjeta.dto.AvisoViajeResponse;
import com.bcra.cards.ws.tarjeta.service.AvisoViajeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Tarjetas", description = "Consultas y gestión de tarjetas del titular autenticado")
@RestController
@RequestMapping("/api/v1/tarjetas")
@RequiredArgsConstructor
public class AvisoViajeController {

    private final AvisoViajeService servicio;

    /** Confirma el registro del destino y fechas con recomendaciones de seguridad. */
    @Operation(summary = "Registrar aviso de viaje",

            description = "Registra el aviso confirmado; "
                    + "no reemplaza políticas internacionales del emisor ni límites del banco.",

            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(name = "Solicitud", value = """
                            {
                              "tarjetaId": "11111111-1111-4111-8111-111111111111",
                              "pais": "España",
                              "region": "Madrid",
                              "fechaDesde": "2026-12-01",
                              "fechaHasta": "2026-12-15"
                            }
                            """))))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Operación exitosa", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/400"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/401"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/403"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/404"),
        @ApiResponse(responseCode = "500", ref = "#/components/responses/500")
    })
    @PostMapping("/{tarjetaId}/aviso-viaje")
    @ResponseStatus(HttpStatus.CREATED)
    public AvisoViajeResponse registrar(@Parameter(description = "Identificador de la tarjeta del titular",
                example = "11111111-1111-4111-8111-111111111111")
            @PathVariable UUID tarjetaId,
            @Parameter(description = "Credencial JWT de sesión validada con H2; requiere vínculo activo",
                    example = "jwt-de-sesion", required = true)
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @Valid @RequestBody AvisoViajeRequest request) {
        return servicio.registrar(credencial, tarjetaId, request);
    }
}
