package com.bcra.cards.ws.tarjeta.controller;

import com.bcra.cards.ws.tarjeta.dto.BloqueoDefinitivoRequest;
import com.bcra.cards.ws.tarjeta.dto.BloqueoDefinitivoResponse;
import com.bcra.cards.ws.tarjeta.dto.BloqueoTemporalRequest;
import com.bcra.cards.ws.tarjeta.dto.BloqueoTemporalResponse;
import com.bcra.cards.ws.tarjeta.dto.DesbloqueoRequest;
import com.bcra.cards.ws.tarjeta.dto.TarjetaResponse;
import com.bcra.cards.ws.tarjeta.service.BloqueoService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operaciones de bloqueo de las tarjetas del titular autenticado. */
@Tag(name = "Tarjetas", description = "Consultas y gestión de tarjetas del titular autenticado")
@RestController
@RequestMapping("/api/v1/tarjetas")
@RequiredArgsConstructor
public class BloqueoController {

    private final BloqueoService bloqueoService;

    /** Bloquea definitivamente tras recibir las dos confirmaciones del cliente. */
    @Operation(summary = "Bloquear definitivamente",
                description = "Exige motivo ROBO/EXTRAVIO y dos CONFIRMAR. "
                    + "Advertir irreversibilidad por WhatsApp antes de confirmar.",

            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", examples = {
                        @ExampleObject(name = "Robo", value = """
                            {
                              "tarjetaId": "11111111-1111-4111-8111-111111111111",
                              "motivo": "ROBO",
                              "confirmarPrimero": "CONFIRMAR",
                              "confirmarSegundo": "CONFIRMAR"
                            }
                            """),
                        @ExampleObject(name = "Extravio", value = """
                            {
                              "tarjetaId": "11111111-1111-4111-8111-111111111111",
                              "motivo": "EXTRAVIO",
                              "confirmarPrimero": "CONFIRMAR",
                              "confirmarSegundo": "CONFIRMAR"
                            }
                            """)})))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operación exitosa", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/400"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/401"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/403"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/404"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/409"),
        @ApiResponse(responseCode = "500", ref = "#/components/responses/500")
    })
    @PostMapping("/{tarjetaId}/bloquear/definitivo")
    public BloqueoDefinitivoResponse bloquearDefinitivo(@Parameter(
            description = "Identificador de la tarjeta del titular",
                example = "11111111-1111-4111-8111-111111111111")
            @PathVariable UUID tarjetaId,
            @Parameter(description = "Credencial JWT de sesión validada con H2; requiere vínculo activo",
                    example = "jwt-de-sesion", required = true)
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @Valid @RequestBody BloqueoDefinitivoRequest solicitud) {
        return bloqueoService.bloquearDefinitivo(tarjetaId, credencial, solicitud);
    }

    /** Bloquea temporalmente tras recibir confirmación explícita. */
    @Operation(summary = "Bloquear temporalmente",

            description = "Exige tarjetaId coincidente y confirmación exacta CONFIRMAR. Solo admite tarjeta activa.",

            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(name = "Solicitud", value = """
                            {
                              "tarjetaId": "11111111-1111-4111-8111-111111111111",
                              "confirmar": "CONFIRMAR"
                            }
                            """))))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operación exitosa", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/400"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/401"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/403"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/404"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/409"),
        @ApiResponse(responseCode = "500", ref = "#/components/responses/500")
    })
    @PostMapping("/{tarjetaId}/bloquear/temporal")
    public BloqueoTemporalResponse bloquearTemporal(@Parameter(description = "Identificador de la tarjeta del titular",
                example = "11111111-1111-4111-8111-111111111111")
            @PathVariable UUID tarjetaId,
            @Parameter(description = "Credencial JWT de sesión validada con H2; requiere vínculo activo",
                    example = "jwt-de-sesion", required = true)
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @Valid @RequestBody BloqueoTemporalRequest request) {
        return bloqueoService.bloquearTemporal(credencial, tarjetaId, request);
    }

    /** Reactiva una tarjeta con bloqueo temporal tras la validación requerida. */
    @Operation(summary = "Desbloquear temporalmente",

            description = "Exige validación adicional según configuración. "
                    + "Un bloqueo definitivo no se revierte por chat.",

            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(name = "Solicitud", value = """
                            {
                              "tarjetaId": "11111111-1111-4111-8111-111111111111",
                              "validacionAdicional": true
                            }
                            """))))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operación exitosa", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/400"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/401"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/403"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/404"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/409"),
        @ApiResponse(responseCode = "500", ref = "#/components/responses/500")
    })
    @PostMapping("/{tarjetaId}/desbloquear/temporal")
    public TarjetaResponse desbloquearTemporal(@Parameter(description = "Identificador de la tarjeta del titular",
                example = "11111111-1111-4111-8111-111111111111")
            @PathVariable UUID tarjetaId,
            @Parameter(description = "Credencial JWT de sesión validada con H2; requiere vínculo activo",
                    example = "jwt-de-sesion", required = true)
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @Valid @RequestBody DesbloqueoRequest solicitud) {
        return bloqueoService.desbloquearTemporal(tarjetaId, credencial, solicitud);
    }
}
