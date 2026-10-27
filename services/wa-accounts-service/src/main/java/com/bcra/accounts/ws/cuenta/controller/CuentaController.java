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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.accounts.ws.cuenta.dto.CuentaResponse;
import com.bcra.accounts.ws.cuenta.dto.SaldoResponse;
import com.bcra.accounts.ws.cuenta.service.CuentaService;
import com.bcra.accounts.ws.exception.ErrorResponse;

import lombok.RequiredArgsConstructor;

/** API de consulta de cuentas autenticada mediante credencial de sesión de H2. */
@RestController
@RequestMapping("/api/v1/cuentas")
@RequiredArgsConstructor
@Tag(name = "Cuenta", description = "Operaciones del titular autenticado mediante H2")
public class CuentaController {

    private final CuentaService cuentaService;

    /** Devuelve las cuentas del titular o 401 si falta una credencial válida. */
    @Operation(summary = "Listar cuentas",
            description = "Devuelve exclusivamente las cuentas del titular; puede devolver una lista vacía.")
    @ApiResponse(responseCode = "200", description = "Listar cuentas",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = CuentaResponse.class)),
                    examples = @ExampleObject(name = "Cuentas", ref = "#/components/examples/Cuentas")))
    @ApiResponse(responseCode = "401", description = "Credencial ausente, expirada o revocada",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "Unauthorized", ref = "#/components/examples/Unauthorized")))
    @ApiResponse(responseCode = "500", description = "Error interno o validación H2 no disponible",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "InternalError", ref = "#/components/examples/InternalError")))
    @GetMapping
    public List<CuentaResponse> listar(
            @Parameter(description = "JWT de sesión emitido por H2; se valida en cada solicitud",
                    example = "credencial-de-sesion-h2")
            @RequestHeader(value = "X-Credencial-Sesion", defaultValue = "") String credencial) {
        return cuentaService.listar(credencial);
    }

    /** Devuelve los saldos de la cuenta propia indicada, o 404 si no pertenece al titular. */
    @Operation(summary = "Consultar saldo",
            description = "Devuelve saldo disponible y contable en la moneda de la cuenta propia.")
    @ApiResponse(responseCode = "200", description = "Consultar saldo",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = SaldoResponse.class),
                    examples = @ExampleObject(name = "Saldo", ref = "#/components/examples/Saldo")))
    @ApiResponse(responseCode = "404", description = "Recurso inexistente o ajeno al titular",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "NotFound", ref = "#/components/examples/NotFound")))
    @ApiResponse(responseCode = "401", description = "Credencial ausente, expirada o revocada",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "Unauthorized", ref = "#/components/examples/Unauthorized")))
    @ApiResponse(responseCode = "500", description = "Error interno o validación H2 no disponible",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "InternalError", ref = "#/components/examples/InternalError")))
    @GetMapping("/{numeroCuenta}/saldo")
    public SaldoResponse consultarSaldo(
            @Parameter(description = "JWT de sesión emitido por H2; se valida en cada solicitud",
                    example = "credencial-de-sesion-h2")
            @RequestHeader(value = "X-Credencial-Sesion", defaultValue = "") String credencial,
            @Parameter(description = "Número de cuenta del titular", example = "001")
            @PathVariable String numeroCuenta) {
        return cuentaService.consultarSaldo(credencial, numeroCuenta);
    }
}
