package com.bcra.channel.ws.binding.controller;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.channel.ws.binding.dto.ChannelProfileResponse;
import com.bcra.channel.ws.binding.dto.UpdatePreferencesRequest;
import com.bcra.channel.ws.binding.service.ChannelProfileService;
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
 * API de perfil y preferencias del canal sobre vínculos activos.
 */
@RestController
@RequestMapping("/api/v1/bindings")
@RequiredArgsConstructor
@Tag(name = "Perfil del canal", description = "Consulta de perfil y actualización de preferencias")
public class ChannelProfileController {

    private final ChannelProfileService channelProfileService;

    /**
     * Consulta el perfil del canal de un vínculo activo.
     *
     * @param whatsappNumber número WhatsApp en E.164
     * @return perfil con nombre público y preferencias
     */
    @GetMapping(path = "/{whatsappNumber}/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Consultar perfil del canal",
            description = "Devuelve datos públicos y preferencias de un vínculo ACTIVO. "
                    + "Sin vínculo activo responde 404. No incluye datos legales sensibles.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Perfil del canal",
                    content = @Content(
                            schema = @Schema(implementation = ChannelProfileResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "whatsappNumber": "+5491112345678",
                                      "clientId": "c1d2e3f4-a5b6-7890-cdef-1234567890ab",
                                      "displayName": "Ana Pérez",
                                      "bindingStatus": "ACTIVO",
                                      "languagePreference": "es",
                                      "notificationsEnabled": true,
                                      "createdAt": "2026-11-27T12:00:00Z",
                                      "updatedAt": "2026-11-27T12:00:00Z"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Número con formato inválido",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/BadRequest"))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Sin vínculo activo",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/NotFound"))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno (p. ej. H1 no responde)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/InternalError")))
    })
    public ChannelProfileResponse getProfile(
            @Parameter(
                    description = "Número WhatsApp en formato E.164",
                    example = "+5491112345678",
                    required = true)
            @PathVariable String whatsappNumber) {
        return channelProfileService.getProfile(whatsappNumber);
    }

    /**
     * Actualiza idioma y notificaciones de un vínculo activo.
     *
     * @param whatsappNumber número WhatsApp en E.164
     * @param request        preferencias
     * @return perfil actualizado
     */
    @PatchMapping(
            path = "/{whatsappNumber}/preferences",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Actualizar preferencias del canal",
            description = "Guarda idioma y notificaciones sobre un vínculo ACTIVO y devuelve el perfil.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Preferencias actualizadas",
                    content = @Content(
                            schema = @Schema(implementation = ChannelProfileResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "whatsappNumber": "+5491112345678",
                                      "clientId": "c1d2e3f4-a5b6-7890-cdef-1234567890ab",
                                      "displayName": "Ana Pérez",
                                      "bindingStatus": "ACTIVO",
                                      "languagePreference": "en",
                                      "notificationsEnabled": false,
                                      "createdAt": "2026-11-27T12:00:00Z",
                                      "updatedAt": "2026-11-27T13:00:00Z"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Campos inválidos",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/ValidationError"))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Sin vínculo activo",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/NotFound"))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/InternalError")))
    })
    public ChannelProfileResponse updatePreferences(
            @Parameter(
                    description = "Número WhatsApp en formato E.164",
                    example = "+5491112345678",
                    required = true)
            @PathVariable String whatsappNumber,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = UpdatePreferencesRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "languagePreference": "en",
                                      "notificationsEnabled": false
                                    }
                                    """)))
            @Valid @RequestBody UpdatePreferencesRequest request) {
        return channelProfileService.updatePreferences(whatsappNumber, request);
    }
}
