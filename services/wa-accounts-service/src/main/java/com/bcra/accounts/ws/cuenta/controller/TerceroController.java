package com.bcra.accounts.ws.cuenta.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.accounts.ws.cuenta.dto.TerceroResponse;
import com.bcra.accounts.ws.cuenta.service.TerceroService;
import com.bcra.accounts.ws.exception.ErrorResponse;

import lombok.RequiredArgsConstructor;

/** API de consulta de destinatarios agendados por el titular autenticado. */
@RestController
@RequestMapping("/api/v1/terceros")
@RequiredArgsConstructor
@Tag(name = "Tercero", description = "Operaciones del titular autenticado mediante H2")
public class TerceroController {

    private final TerceroService terceroService;

    /** Devuelve los terceros activos del titular o 401 si falta una credencial válida. */
    @Operation(summary = "Listar terceros",
            description = "Devuelve solo los destinatarios activos agendados del titular; puede devolver"
                    + " una lista vacía.")
    @ApiResponse(responseCode = "200", description = "Listar terceros",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = TerceroResponse.class)),
                    examples = @ExampleObject(name = "Terceros", ref = "#/components/examples/Terceros")))
    @ApiResponse(responseCode = "401", description = "Credencial ausente, expirada o revocada",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "Unauthorized", ref = "#/components/examples/Unauthorized")))
    @ApiResponse(responseCode = "500", description = "Error interno o validación H2 no disponible",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "InternalError", ref = "#/components/examples/InternalError")))
    @GetMapping
    public List<TerceroResponse> listar(
            @Parameter(description = "JWT de sesión emitido por H2; se valida en cada solicitud",
                    example = "credencial-de-sesion-h2")
            @RequestHeader(value = "X-Credencial-Sesion", defaultValue = "") String credencial) {
        return terceroService.listar(credencial);
    }
}
