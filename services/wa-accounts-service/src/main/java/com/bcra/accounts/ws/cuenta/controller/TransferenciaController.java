package com.bcra.accounts.ws.cuenta.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.accounts.ws.cuenta.dto.ConfirmarTransferenciaRequest;
import com.bcra.accounts.ws.cuenta.dto.TransferenciaPreparadaResponse;
import com.bcra.accounts.ws.cuenta.dto.TransferenciaRequest;
import com.bcra.accounts.ws.cuenta.dto.TransferenciaResponse;
import com.bcra.accounts.ws.cuenta.service.TransferenciaService;
import com.bcra.accounts.ws.exception.ErrorResponse;

import lombok.RequiredArgsConstructor;

/** API de preparación y confirmación explícita de transferencias. */
@RestController
@RequestMapping("/api/v1/transferencias")
@RequiredArgsConstructor
@Tag(name = "Transferencia", description = "Operaciones del titular autenticado mediante H2")
public class TransferenciaController {

    private final TransferenciaService service;

    /** Confirma explícitamente; devuelve CONFIRMADA si resta la segunda confirmación, o EJECUTADA. */
    @Operation(summary = "Confirmar transferencia",
            description = "Requiere CONFIRMAR literal. CONFIRMADA devuelve 200 sin débito y exige otra"
                    + " confirmación consecutiva; EJECUTADA incluye comprobante. Otra respuesta"
                    + " reinicia a PREPARADA y devuelve 409. Se revalidan vencimiento y reglas;"
                    + " destinos no locales se rechazan sin débito.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = ConfirmarTransferenciaRequest.class),
                            examples = @ExampleObject(value = "{\"confirmacion\":\"CONFIRMAR\"}"))))
    @ApiResponse(responseCode = "400", description = "JSON o UUID inválido",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "BadRequest", ref = "#/components/examples/BadRequest")))
    @ApiResponse(responseCode = "404", description = "Recurso inexistente o ajeno al titular",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "NotFoundTransferencia",
                            ref = "#/components/examples/NotFoundTransferencia")))
    @ApiResponse(responseCode = "409", description = "Rechazo por saldo, límites, horario, moneda, destino o estado",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "Conflict", ref = "#/components/examples/Conflict")))
    @ApiResponse(responseCode = "401", description = "Credencial ausente, expirada o revocada",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "Unauthorized", ref = "#/components/examples/Unauthorized")))
    @ApiResponse(responseCode = "500", description = "Error interno o validación H2 no disponible",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "InternalError", ref = "#/components/examples/InternalError")))
    @ApiResponse(responseCode = "200", description = "Ejecutada o segunda confirmación pendiente",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = TransferenciaResponse.class), examples = {
                @ExampleObject(name = "Ejecutada", ref = "#/components/examples/Ejecutada"),
                @ExampleObject(name = "Confirmada", ref = "#/components/examples/Confirmada")
            }))
    @PostMapping("/{id}/confirmar")
    public TransferenciaResponse confirmar(@Parameter(description = "Identificador devuelto al preparar",
                    example = "22222222-2222-4222-8222-222222222222") @PathVariable UUID id,
            @Parameter(description = "JWT de sesión emitido por H2; se valida en cada solicitud",
                    example = "credencial-de-sesion-h2")
            @RequestHeader(value = "X-Credencial-Sesion", defaultValue = "") String credencial,
            @RequestBody ConfirmarTransferenciaRequest solicitud) {
        return service.confirmar(credencial, id, solicitud);
    }

    /** Valida sesión y reglas operativas; devuelve 201 con resumen y vencimiento sin efectuar débito. */
    @Operation(summary = "Preparar transferencia",
            description = "Valida sesión, saldo, límites, moneda y horario. Reserva fondos sin débito."
                    + " Indicar cuentaDestino propia o terceroId agendado, exclusivamente. Expira en 5"
                    + " minutos por defecto.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = TransferenciaRequest.class), examples = {
                        @ExampleObject(name = "Propia", value = """
                                {"cuentaOrigen":"001","cuentaDestino":"002","monto":100.00,
                                 "moneda":"ARS","concepto":"Transferencia"}
                                """),
                        @ExampleObject(name = "Tercero", value = """
                                {"cuentaOrigen":"001","terceroId":"11111111-1111-4111-8111-111111111111",
                                 "monto":100.00,"moneda":"ARS","concepto":"Transferencia"}
                                """)
                    })))
    @ApiResponse(responseCode = "201", description = "Preparar transferencia",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = TransferenciaPreparadaResponse.class),
                    examples = @ExampleObject(name = "Preparada", ref = "#/components/examples/Preparada")))
    @ApiResponse(responseCode = "400", description = "Solicitud o parámetros inválidos",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = {
                        @ExampleObject(name = "BadRequest", ref = "#/components/examples/BadRequest"),
                        @ExampleObject(name = "ValidationError", ref = "#/components/examples/ValidationError")
                    }))
    @ApiResponse(responseCode = "404", description = "Recurso inexistente o ajeno al titular",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "NotFound", ref = "#/components/examples/NotFound")))
    @ApiResponse(responseCode = "409", description = "Rechazo por saldo, límites, horario, moneda, destino o estado",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "Conflict", ref = "#/components/examples/Conflict")))
    @ApiResponse(responseCode = "401", description = "Credencial ausente, expirada o revocada",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "Unauthorized", ref = "#/components/examples/Unauthorized")))
    @ApiResponse(responseCode = "500", description = "Error interno o validación H2 no disponible",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "InternalError", ref = "#/components/examples/InternalError")))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferenciaPreparadaResponse preparar(
            @Parameter(description = "JWT de sesión emitido por H2; se valida en cada solicitud",
                    example = "credencial-de-sesion-h2")
            @RequestHeader(value = "X-Credencial-Sesion", defaultValue = "") String credencial,
            @Valid @RequestBody TransferenciaRequest solicitud) {
        return service.preparar(credencial, solicitud);
    }
}

