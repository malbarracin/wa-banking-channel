package com.bcra.accounts.ws.cuenta.controller;

import java.time.Instant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.accounts.ws.cuenta.dto.MovimientosPageResponse;
import com.bcra.accounts.ws.cuenta.entity.TipoMovimiento;
import com.bcra.accounts.ws.cuenta.service.MovimientoService;
import com.bcra.accounts.ws.exception.ErrorResponse;

import lombok.RequiredArgsConstructor;

/** Consultas paginadas de movimientos del titular autenticado. */
@RestController
@RequestMapping("/api/v1/cuentas/{numero}/movimientos")
@RequiredArgsConstructor
@Tag(name = "Movimiento", description = "Operaciones del titular autenticado mediante H2")
public class MovimientoController {

    private final MovimientoService service;

    /** Devuelve movimientos de la cuenta; fechas ISO-8601 UTC y cursor de la respuesta anterior. */
    @Operation(summary = "Consultar movimientos",
            description = "Orden descendente por fecha e id. Ventana máxima configurable (90 días por"
                    + " defecto). Sin fechas usa la ventana hasta ahora; al continuar conserva filtros"
                    + " y ventana del cursor.")
    @ApiResponse(responseCode = "200", description = "Consultar movimientos",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = MovimientosPageResponse.class),
                    examples = @ExampleObject(name = "Movimientos", ref = "#/components/examples/Movimientos")))
    @ApiResponse(responseCode = "400", description = "Solicitud o parámetros inválidos",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "BadRequest", ref = "#/components/examples/BadRequest")))
    @ApiResponse(responseCode = "404", description = "Recurso inexistente o ajeno al titular",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "NotFoundMovimiento",
                            ref = "#/components/examples/NotFoundMovimiento")))
    @ApiResponse(responseCode = "401", description = "Credencial ausente, expirada o revocada",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "Unauthorized", ref = "#/components/examples/Unauthorized")))
    @ApiResponse(responseCode = "500", description = "Error interno o validación H2 no disponible",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "InternalError", ref = "#/components/examples/InternalError")))
    @GetMapping
    public MovimientosPageResponse listar(
            @Parameter(description = "JWT de sesión emitido por H2; se valida en cada solicitud",
                    example = "credencial-de-sesion-h2")
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @Parameter(description = "Número de cuenta del titular", example = "001")
            @PathVariable String numero,
            @Parameter(description = "Inicio inclusivo ISO-8601 UTC", example = "2026-10-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @Parameter(description = "Fin inclusivo ISO-8601 UTC", example = "2026-10-27T12:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
            @Parameter(description = "Filtrar débitos o créditos", example = "DEBITO")
            @RequestParam(required = false) TipoMovimiento tipo,
            @Parameter(description = "Tamaño positivo, limitado al tope configurado (10 por defecto)", example = "10")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Cursor opaco Base64 de la respuesta anterior; omitir en la primera página",
                    example = "")
            @RequestParam(required = false) String cursor) {
        return service.listar(credencial, numero, desde, hasta, tipo, limit, cursor);
    }
}
