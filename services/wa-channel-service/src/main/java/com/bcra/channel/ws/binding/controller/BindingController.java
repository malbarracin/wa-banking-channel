package com.bcra.channel.ws.binding.controller;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.channel.ws.binding.dto.AcceptTermsRequest;
import com.bcra.channel.ws.binding.dto.AcceptTermsResponse;
import com.bcra.channel.ws.binding.dto.BindingStatusResponse;
import com.bcra.channel.ws.binding.service.BindingService;
import com.bcra.channel.ws.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@Tag(name = "Vínculo", description = "Estado del vínculo y aceptación de términos")
public class BindingController {

    private final BindingService bindingService;

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
                            examples = @ExampleObject(ref = "#/components/examples/BadRequest"))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/InternalError")))
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
                            examples = @ExampleObject(ref = "#/components/examples/BadRequest"))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Vínculo ya activo u otra transición inválida",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/Conflict"))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/InternalError")))
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
}
