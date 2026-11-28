package com.bcra.channel.ws.binding.controller;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.channel.ws.binding.dto.AcceptTermsRequest;
import com.bcra.channel.ws.binding.dto.AcceptTermsResponse;
import com.bcra.channel.ws.binding.dto.BindingStatusResponse;
import com.bcra.channel.ws.binding.dto.BlockBindingRequest;
import com.bcra.channel.ws.binding.dto.BlockBindingResponse;
import com.bcra.channel.ws.binding.dto.UnbindRequest;
import com.bcra.channel.ws.binding.dto.UnbindResponse;
import com.bcra.channel.ws.binding.dto.VerifyIdentityRequest;
import com.bcra.channel.ws.binding.dto.VerifyIdentityResponse;
import com.bcra.channel.ws.binding.service.BindingLifecycleService;
import com.bcra.channel.ws.binding.service.BindingService;
import com.bcra.channel.ws.config.BankInternalAuthFilter;
import com.bcra.channel.ws.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * API del vínculo número WhatsApp ↔ cliente del banco.
 */
@RestController
@RequestMapping("/api/v1/bindings")
@RequiredArgsConstructor
@Tag(name = "Vínculo", description = "Estado del vínculo, términos, verificación de identidad, bloqueo y desvinculación")
public class BindingController {

    private final BindingService bindingService;
    private final BindingLifecycleService bindingLifecycleService;

    /**
     * Consulta el estado del vínculo de un número.
     *
     * @param whatsappNumber número WhatsApp en E.164
     * @return estado actual (SIN_VINCULO si no hay registro)
     */
    @GetMapping(path = "/{whatsappNumber}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Consultar estado del vínculo",
            description = "Devuelve el estado del número. Sin registro responde 200 con status SIN_VINCULO.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Estado del vínculo",
                    content = @Content(
                            schema = @Schema(implementation = BindingStatusResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Activo",
                                            value = """
                                                    {
                                                      "whatsappNumber": "+5491112345678",
                                                      "status": "ACTIVO",
                                                      "clientId": "c1d2e3f4-a5b6-7890-cdef-1234567890ab",
                                                      "bindingId": "b2c3d4e5-f6a7-8901-bcde-f12345678901"
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "SinVinculo",
                                            value = """
                                                    {
                                                      "whatsappNumber": "+5491112345678",
                                                      "status": "SIN_VINCULO",
                                                      "clientId": null,
                                                      "bindingId": null
                                                    }
                                                    """)
                            })),
            @ApiResponse(
                    responseCode = "400",
                    description = "Número con formato inválido",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "BadRequest", ref = "#/components/examples/BadRequest"))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InternalError", ref = "#/components/examples/InternalError")))
    })
    public BindingStatusResponse getStatus(
            @Parameter(
                    description = "Número WhatsApp en formato E.164",
                    example = "+5491112345678",
                    required = true)
            @PathVariable String whatsappNumber) {
        return bindingService.getStatus(whatsappNumber);
    }

    /**
     * Registra la aceptación o el rechazo de términos y condiciones.
     *
     * @param whatsappNumber número WhatsApp en E.164
     * @param request        aceptación explícita
     * @return vínculo pendiente de verificación
     */
    @PostMapping(
            path = "/{whatsappNumber}/terms",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Aceptar o rechazar términos",
            description = "accepted=true crea o reactiva el vínculo en PENDIENTE_VERIFICACION. "
                    + "accepted=false responde 400 con el mensaje de no-aceptación.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Términos aceptados",
                    content = @Content(
                            schema = @Schema(implementation = AcceptTermsResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "bindingId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
                                      "status": "PENDIENTE_VERIFICACION",
                                      "message": "Términos aceptados. Podés continuar con la verificación de identidad."
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Términos rechazados o número inválido",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "BadRequest", ref = "#/components/examples/BadRequest"))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Vínculo ya activo u otra transición inválida",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Conflict", ref = "#/components/examples/Conflict"))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InternalError", ref = "#/components/examples/InternalError")))
    })
    public AcceptTermsResponse acceptTerms(
            @Parameter(
                    description = "Número WhatsApp en formato E.164",
                    example = "+5491112345678",
                    required = true)
            @PathVariable String whatsappNumber,
            @Valid @RequestBody AcceptTermsRequest request) {
        return bindingService.acceptTerms(whatsappNumber, request);
    }

    /**
     * Verifica la identidad del cliente y activa el vínculo si corresponde.
     *
     * @param whatsappNumber número WhatsApp en E.164
     * @param request        clientId de H1 y dato de verificación
     * @return vínculo activo con resultado verificable, o pendiente con intentos restantes
     */
    @PostMapping(
            path = "/{whatsappNumber}/verify",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Verificar identidad y activar vínculo",
            description = "Exige vínculo en PENDIENTE_VERIFICACION. Consulta H1 por clientId, aplica la "
                    + "política de verificación, anti-fraude y emite el resultado verificable. "
                    + "verificationData no se persiste ni se audita.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = VerifyIdentityRequest.class),
                            examples = @ExampleObject(
                                    name = "Verificacion",
                                    value = """
                                            {
                                              "clientId": "123e4567-e89b-12d3-a456-426614174000",
                                              "verificationData": "+5491112345678"
                                            }
                                            """))))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Verificación procesada (éxito o fallo con intentos restantes)",
                    content = @Content(
                            schema = @Schema(implementation = VerifyIdentityResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Exito",
                                            value = """
                                                    {
                                                      "bindingId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
                                                      "status": "ACTIVO",
                                                      "attemptsRemaining": 0,
                                                      "verifiableResult": {
                                                        "identityVerified": true,
                                                        "bindingId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
                                                        "clientId": "123e4567-e89b-12d3-a456-426614174000",
                                                        "verifiedAt": "2026-11-27T15:30:00Z"
                                                      },
                                                      "message": "¡Listo, Ana Pérez! Tu WhatsApp quedó vinculado de forma segura."
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "FalloConIntentos",
                                            value = """
                                                    {
                                                      "bindingId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
                                                      "status": "PENDIENTE_VERIFICACION",
                                                      "attemptsRemaining": 2,
                                                      "message": "No pudimos validar tus datos. Te quedan 2 intentos."
                                                    }
                                                    """)
                            })),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos inválidos",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "BadRequest", ref = "#/components/examples/BadRequest"))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cliente no encontrado en el registro del banco",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "NotFound", ref = "#/components/examples/NotFound"))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Sin términos, cliente suspendido/inactivo o número ya vinculado",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Conflict", ref = "#/components/examples/Conflict"))),
            @ApiResponse(
                    responseCode = "423",
                    description = "Intentos de verificación agotados",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "VerificationLocked", ref = "#/components/examples/VerificationLocked"))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InternalError", ref = "#/components/examples/InternalError")))
    })
    public VerifyIdentityResponse verify(
            @Parameter(
                    description = "Número WhatsApp en formato E.164",
                    example = "+5491112345678",
                    required = true)
            @PathVariable String whatsappNumber,
            @Valid @RequestBody VerifyIdentityRequest request) {
        return bindingService.verify(whatsappNumber, request);
    }

    /**
     * Bloquea el vínculo (cliente confirmado o política del banco).
     *
     * @param whatsappNumber     número WhatsApp en E.164
     * @param request            actor, motivo y confirmación
     * @param internalRoleHeader {@code X-Internal-Role} obligatorio si actor=BANCO
     * @return vínculo bloqueado con indicador de revocación de credencial
     */
    @PostMapping(
            path = "/{whatsappNumber}/block",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Bloquear vínculo",
            description = "ACTIVO → BLOQUEADO. Cliente requiere confirmed=true. "
                    + "BANCO requiere header X-Internal-Role con el rol bank-policy configurado. "
                    + "La respuesta indica credentialRevocationRequired para revocar en el mismo intercambio.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Vínculo bloqueado",
                    content = @Content(
                            schema = @Schema(implementation = BlockBindingResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "bindingId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
                                      "status": "BLOQUEADO",
                                      "message": "Tu WhatsApp quedó bloqueado para operaciones. Para reactivarlo, seguí los pasos de verificación cuando quieras.",
                                      "credentialRevocationRequired": true
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Actor ausente, sin confirmación del cliente o número inválido",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "BadRequest", ref = "#/components/examples/BadRequest"))),
            @ApiResponse(
                    responseCode = "403",
                    description = "BANCO sin X-Internal-Role válido",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Forbidden", ref = "#/components/examples/Forbidden"))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Vínculo inexistente",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "NotFound", ref = "#/components/examples/NotFound"))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Ya bloqueado, desvinculado u otra transición inválida",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Conflict", ref = "#/components/examples/Conflict"))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InternalError", ref = "#/components/examples/InternalError")))
    })
    public BlockBindingResponse block(
            @Parameter(
                    description = "Número WhatsApp en formato E.164",
                    example = "+5491112345678",
                    required = true)
            @PathVariable String whatsappNumber,
            @Valid @RequestBody BlockBindingRequest request,
            @Parameter(
                    name = BankInternalAuthFilter.HEADER_NAME,
                    description = "Rol interno; obligatorio si actor=BANCO (valor bank-policy)",
                    in = ParameterIn.HEADER,
                    example = "bank-policy")
            @RequestHeader(value = BankInternalAuthFilter.HEADER_NAME, required = false)
            String internalRoleHeader) {
        return bindingLifecycleService.block(whatsappNumber, request, internalRoleHeader);
    }

    /**
     * Desvincula el número (cliente confirmado o política del banco).
     *
     * @param whatsappNumber     número WhatsApp en E.164
     * @param request            actor, motivo y confirmación
     * @param internalRoleHeader {@code X-Internal-Role} obligatorio si actor=BANCO
     * @return vínculo desvinculado con indicador de revocación de credencial
     */
    @PostMapping(
            path = "/{whatsappNumber}/unbind",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Desvincular número",
            description = "ACTIVO o BLOQUEADO → DESVINCULADO. Cliente requiere confirmed=true. "
                    + "BANCO requiere header X-Internal-Role con el rol bank-policy configurado. "
                    + "La respuesta indica credentialRevocationRequired para revocar en el mismo intercambio.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Número desvinculado",
                    content = @Content(
                            schema = @Schema(implementation = UnbindResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "bindingId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
                                      "status": "DESVINCULADO",
                                      "message": "Tu número fue desvinculado del canal. Si querés volver a operar por WhatsApp, escribinos y repetimos la verificación.",
                                      "credentialRevocationRequired": true
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Actor ausente, sin confirmación del cliente o número inválido",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "BadRequest", ref = "#/components/examples/BadRequest"))),
            @ApiResponse(
                    responseCode = "403",
                    description = "BANCO sin X-Internal-Role válido",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Forbidden", ref = "#/components/examples/Forbidden"))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Vínculo inexistente",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "NotFound", ref = "#/components/examples/NotFound"))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Ya desvinculado u otra transición inválida",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Conflict", ref = "#/components/examples/Conflict"))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InternalError", ref = "#/components/examples/InternalError")))
    })
    public UnbindResponse unbind(
            @Parameter(
                    description = "Número WhatsApp en formato E.164",
                    example = "+5491112345678",
                    required = true)
            @PathVariable String whatsappNumber,
            @Valid @RequestBody UnbindRequest request,
            @Parameter(
                    name = BankInternalAuthFilter.HEADER_NAME,
                    description = "Rol interno; obligatorio si actor=BANCO (valor bank-policy)",
                    in = ParameterIn.HEADER,
                    example = "bank-policy")
            @RequestHeader(value = BankInternalAuthFilter.HEADER_NAME, required = false)
            String internalRoleHeader) {
        return bindingLifecycleService.unbind(whatsappNumber, request, internalRoleHeader);
    }
}
