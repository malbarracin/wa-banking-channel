package com.bcra.credentials.ws.credencial.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.credentials.ws.credencial.dto.EmitirCredencialRequest;
import com.bcra.credentials.ws.credencial.dto.EmitirCredencialResponse;
import com.bcra.credentials.ws.credencial.dto.HistorialCredencialResponse;
import com.bcra.credentials.ws.credencial.dto.RevocacionMasivaRequest;
import com.bcra.credentials.ws.credencial.dto.RevocacionMasivaResponse;
import com.bcra.credentials.ws.credencial.dto.RevocarCredencialRequest;
import com.bcra.credentials.ws.credencial.dto.ValidarCredencialRequest;
import com.bcra.credentials.ws.credencial.dto.ValidarCredencialResponse;
import com.bcra.credentials.ws.credencial.service.CredencialService;
import com.bcra.credentials.ws.credencial.service.HistorialCredencialService;
import com.bcra.credentials.ws.credencial.service.RevocacionMasivaService;
import com.bcra.credentials.ws.credencial.service.ValidacionService;
import com.bcra.credentials.ws.exception.ErrorResponse;

import lombok.RequiredArgsConstructor;

/** API interna de credenciales para el canal. */
@RestController
@RequestMapping("/api/v1/credenciales")
@Validated
@RequiredArgsConstructor
@Tag(name = "Credenciales", description = "API interna H2 de acceso y sesión del canal WhatsApp")
public class CredencialController {

    private final ValidacionService validacionService;
    private final CredencialService credencialService;
    private final RevocacionMasivaService revocacionMasivaService;
    private final HistorialCredencialService historialCredencialService;

    /** @return estado actual y eventos de soporte, o 404 si falta la credencial o su historial. */
    @Operation(summary = "Consultar historial",
            description =
                    "Devuelve identidad y estado efectivo de la última credencial y eventos "
                    + "descendentes. Calcula EXPIRADA sin modificar la persistencia; no "
                    + "incluye JWT ni jti.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operación confirmada",
                content = @Content(schema = @Schema(implementation = HistorialCredencialResponse.class),
                        examples = @ExampleObject(name = "Exito", value = """
                                {
                                  "numeroWhatsapp": "+5491111111111",
                                  "documentoTipo": "DNI",
                                  "documentoNumero": "12345678",
                                  "estadoActual": "ACTIVA",
                                  "eventos": [
                                    {
                                      "accion": "EMISION",
                                      "resultado": "EXITOSO",
                                      "fecha": "2026-10-26T12:00:00Z",
                                      "motivo": "EMISION"
                                    }
                                  ]
                                }
                                """))),
        @ApiResponse(responseCode = "400", description = "Número inválido",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = {@ExampleObject(name = "ValidationNumeroHistorial",
                                ref = "#/components/examples/ValidationNumeroHistorial"),
                            @ExampleObject(name = "BadRequest", ref = "#/components/examples/BadRequest")})),
        @ApiResponse(responseCode = "404", description = "No existe credencial o historial",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = {@ExampleObject(name = "NotFoundHistorial",
                                ref = "#/components/examples/NotFoundHistorial")})),
        @ApiResponse(responseCode = "500", description = "Error interno sin detalles sensibles",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = {@ExampleObject(name = "InternalError",
                                ref = "#/components/examples/InternalError")}))
    })
    @GetMapping("/{numero}/historial")
    public HistorialCredencialResponse consultarHistorial(
            @Parameter(description = "Número de WhatsApp E.164", example = "+5491111111111")
            @PathVariable @Pattern(regexp = "\\+[1-9][0-9]{1,14}") String numero) {
        return historialCredencialService.consultar(numero);
    }

    /** @return cantidad revocada por decisión del banco, con auditoría por credencial. */
    @Operation(summary = "Revocar credenciales masivamente",
            description =
                    "Decisión del banco: revoca todas las activas o las de un cliente en "
                    + "todos sus números. Cambios y auditorías se confirman atómicamente. Sin "
                    + "coincidencias devuelve revocadas = 0.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = RevocacionMasivaRequest.class),
                            examples = {@ExampleObject(name = "Todas", value = """
                                {
                                  "criterio": "TODAS"
                                }
                                """),
                                @ExampleObject(name = "PorCliente", value = """
                                {
                                  "criterio": "POR_CLIENTE",
                                  "segmento": {
                                    "documentoTipo": "DNI",
                                    "documentoNumero": "12345678"
                                  }
                                }
                                """)})))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operación confirmada",
                content = @Content(schema = @Schema(implementation = RevocacionMasivaResponse.class),
                        examples = @ExampleObject(name = "Exito", value = """
                                {
                                  "revocadas": 1
                                }
                                """))),
        @ApiResponse(responseCode = "400", description = "JSON, criterio o segmento inválidos",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = {@ExampleObject(name = "ValidationAlcance",
                                ref = "#/components/examples/ValidationAlcance"),
                            @ExampleObject(name = "BadRequest", ref = "#/components/examples/BadRequest")})),
        @ApiResponse(responseCode = "500", description = "Error interno sin detalles sensibles",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = {@ExampleObject(name = "InternalError",
                                ref = "#/components/examples/InternalError")})),
        @ApiResponse(responseCode = "401", description = "Servicio no autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "Unauthorized", ref = "#/components/examples/Unauthorized"))),
        @ApiResponse(responseCode = "403", description = "Se requiere el actor banco",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "Forbidden", ref = "#/components/examples/Forbidden")))
    })
    @SecurityRequirement(name = "ServiceBearer")
    @PostMapping("/revocacion-masiva")
    public RevocacionMasivaResponse revocarMasivamente(@Valid @RequestBody RevocacionMasivaRequest request) {
        return revocacionMasivaService.revocar(request);
    }

    /** Revoca antes de responder; devuelve 404 si no existe credencial activa y 204 al confirmar. */
    @Operation(summary = "Revocar una credencial",
            description =
                    "Revoca inmediatamente la credencial activa del número. Sin activa "
                    + "devuelve 404. Omitir solicitadoPor equivale a CANAL. SOLICITUD_CLIENTE "
                    + "e INTENTOS_AGOTADOS se interpretan como BLOQUEO.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = RevocarCredencialRequest.class),
                            examples = {@ExampleObject(name = "BLOQUEO", value = """
                                {
                                  "motivo": "BLOQUEO",
                                  "solicitadoPor": "CANAL"
                                }
                                """),
                                @ExampleObject(name = "DESVINCULACION", value = """
                                {
                                  "motivo": "DESVINCULACION",
                                  "solicitadoPor": "CANAL"
                                }
                                """),
                                @ExampleObject(name = "FRAUDE", value = """
                                {
                                  "motivo": "FRAUDE",
                                  "solicitadoPor": "CANAL"
                                }
                                """),
                                @ExampleObject(name = "POLITICA_BANCO", value = """
                                {
                                  "motivo": "POLITICA_BANCO",
                                  "solicitadoPor": "BANCO"
                                }
                                """),
                                @ExampleObject(name = "REVOCACION_MASIVA", value = """
                                {
                                  "motivo": "REVOCACION_MASIVA",
                                  "solicitadoPor": "CANAL"
                                }
                                """),
                                @ExampleObject(name = "REEMPLAZO", value = """
                                {
                                  "motivo": "REEMPLAZO",
                                  "solicitadoPor": "CANAL"
                                }
                                """),
                                @ExampleObject(name = "H3SolicitudCliente", value = """
                                {
                                  "motivo": "SOLICITUD_CLIENTE"
                                }
                                """),
                                @ExampleObject(name = "H3IntentosAgotados", value = """
                                {
                                  "motivo": "INTENTOS_AGOTADOS"
                                }
                                """)})))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Revocación confirmada; sin cuerpo", content = @Content),
        @ApiResponse(responseCode = "400", description = "Número, JSON o motivo inválidos",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = {@ExampleObject(name = "ValidationMotivo",
                                ref = "#/components/examples/ValidationMotivo"),
                            @ExampleObject(name = "ValidationNumeroRevocacion",
                                ref = "#/components/examples/ValidationNumeroRevocacion"),
                            @ExampleObject(name = "BadRequest", ref = "#/components/examples/BadRequest")})),
        @ApiResponse(responseCode = "404", description = "No existe credencial activa",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = {@ExampleObject(name = "NotFoundCredencial",
                                ref = "#/components/examples/NotFoundCredencial")})),
        @ApiResponse(responseCode = "500", description = "Error interno sin detalles sensibles",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = {@ExampleObject(name = "InternalError",
                                ref = "#/components/examples/InternalError")})),
        @ApiResponse(responseCode = "401", description = "Servicio no autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "Unauthorized", ref = "#/components/examples/Unauthorized"))),
        @ApiResponse(responseCode = "403", description = "Se requiere el actor H3",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "Forbidden", ref = "#/components/examples/Forbidden")))
    })
    @SecurityRequirement(name = "ServiceBearer")
    @PostMapping("/{numero}/revocacion")
    public ResponseEntity<Void> revocar(
            @Parameter(description = "Número de WhatsApp E.164", example = "+5491111111111")
            @PathVariable @Pattern(regexp = "\\+[1-9][0-9]{1,14}") String numero,
            @Valid @RequestBody RevocarCredencialRequest request) {
        credencialService.revocar(numero, request);
        return ResponseEntity.noContent().build();
    }

    /** @return identidad vigente, o 401 si la credencial no autoriza operar. */
    @Operation(summary = "Validar una credencial",
            description =
                    "Verifica firma, vencimiento, identidad, estado persistido y cliente "
                    + "activo en H1. Responde 401 uniforme ante JWT inválido, expirado, "
                    + "revocado o H1 no disponible; requiere re-verificación. Sustituir el "
                    + "JWT ilustrativo por la credencial obtenida de emitir.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = ValidarCredencialRequest.class),
                            examples = {@ExampleObject(name = "JwtEmitido", value = """
                                {
                                  "credencial": "JWT_DEVUELTO_POR_EMITIR"
                                }
                                """)})))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operación confirmada",
                content = @Content(schema = @Schema(implementation = ValidarCredencialResponse.class),
                        examples = @ExampleObject(name = "Exito", value = """
                                {
                                  "vigente": true,
                                  "numeroWhatsapp": "+5491111111111",
                                  "documentoTipo": "DNI",
                                  "documentoNumero": "12345678",
                                  "fechaExpiracion": "2026-10-27T12:00:00Z"
                                }
                                """))),
        @ApiResponse(responseCode = "400", description = "JSON inválido, body ausente o credencial vacía",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = {@ExampleObject(name = "ValidationError",
                                ref = "#/components/examples/ValidationError"),
                            @ExampleObject(name = "BadRequest", ref = "#/components/examples/BadRequest")})),
        @ApiResponse(responseCode = "401", description = "Credencial no válida para operar",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = {@ExampleObject(name = "Unauthorized",
                                ref = "#/components/examples/Unauthorized")})),
        @ApiResponse(responseCode = "500", description = "Error interno sin detalles sensibles",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = {@ExampleObject(name = "InternalError",
                                ref = "#/components/examples/InternalError")}))
    })
    @PostMapping("/validar")
    public ValidarCredencialResponse validar(@Valid @RequestBody ValidarCredencialRequest request) {
        return validacionService.validar(request.credencial());
    }

    /** @return nueva credencial para H3 y expiración UTC; el canal no debe mostrar el JWT al cliente. */
    @Operation(summary = "Emitir una credencial",
            description =
                    "H3 confirma identidad verificada y vínculo activo; H1 debe confirmar "
                    + "cliente activo. La emisión reemplaza cualquier activa anterior. "
                    + "Vigencia configurable. JWT exclusivo del canal: nunca incluir en "
                    + "mensajes al cliente. El JWT del ejemplo es ilustrativo.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = EmitirCredencialRequest.class),
                            examples = {@ExampleObject(name = "Dni", value = """
                                {
                                  "numeroWhatsapp": "+5491111111111",
                                  "documentoTipo": "DNI",
                                  "documentoNumero": "12345678",
                                  "vinculoActivo": true,
                                  "identidadVerificada": true
                                }
                                """),
                                @ExampleObject(name = "Pasaporte", value = """
                                {
                                  "numeroWhatsapp": "+5491111111111",
                                  "documentoTipo": "PASAPORTE",
                                  "documentoNumero": "AAP123456",
                                  "vinculoActivo": true,
                                  "identidadVerificada": true
                                }
                                """)})))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Operación confirmada",
                content = @Content(schema = @Schema(implementation = EmitirCredencialResponse.class),
                        examples = @ExampleObject(name = "Exito", value = """
                                {
                                  "credencial": "JWT_DEVUELTO_POR_EMITIR",
                                  "fechaExpiracion": "2026-10-27T12:00:00Z"
                                }
                                """))),
        @ApiResponse(responseCode = "400", description = "Datos o JSON inválidos",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = {@ExampleObject(name = "ValidationError",
                                ref = "#/components/examples/ValidationError"),
                            @ExampleObject(name = "BadRequest", ref = "#/components/examples/BadRequest")})),
        @ApiResponse(responseCode = "403", description = "Confirmación o estado del cliente insuficiente",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = {@ExampleObject(name = "Forbidden", ref = "#/components/examples/Forbidden")})),
        @ApiResponse(responseCode = "404", description = "Cliente inexistente en H1",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = {@ExampleObject(name = "NotFoundCliente",
                                ref = "#/components/examples/NotFoundCliente")})),
        @ApiResponse(responseCode = "409", description = "Conflicto de persistencia",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = {@ExampleObject(name = "Conflict", ref = "#/components/examples/Conflict")})),
        @ApiResponse(responseCode = "503", description = "H1 no disponible",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = {@ExampleObject(name = "ServiceUnavailable",
                                ref = "#/components/examples/ServiceUnavailable")})),
        @ApiResponse(responseCode = "500", description = "Error interno sin detalles sensibles",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                        examples = {@ExampleObject(name = "InternalError",
                                ref = "#/components/examples/InternalError")})),
        @ApiResponse(responseCode = "401", description = "Servicio H3 no autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "Unauthorized", ref = "#/components/examples/Unauthorized")))
    })
    @SecurityRequirement(name = "ServiceBearer")
    @PostMapping("/emitir")
    public ResponseEntity<EmitirCredencialResponse> emitir(@Valid @RequestBody EmitirCredencialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(credencialService.emitir(request));
    }
}
