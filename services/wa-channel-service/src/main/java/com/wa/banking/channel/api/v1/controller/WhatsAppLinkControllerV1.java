package com.wa.banking.channel.api.v1.controller;

import com.wa.banking.channel.api.error.ErrorResponse;
import com.wa.banking.channel.api.v1.dto.AcceptTermsRequestV1;
import com.wa.banking.channel.api.v1.dto.BlockLinkRequestV1;
import com.wa.banking.channel.api.v1.dto.InitiateLinkRequestV1;
import com.wa.banking.channel.api.v1.dto.InteractionHistoryItemV1;
import com.wa.banking.channel.api.v1.dto.LinkResponseV1;
import com.wa.banking.channel.api.v1.dto.PreferencesRequestV1;
import com.wa.banking.channel.api.v1.dto.PreferencesResponseV1;
import com.wa.banking.channel.api.v1.dto.ProfileResponseV1;
import com.wa.banking.channel.api.v1.dto.RelinkRequestV1;
import com.wa.banking.channel.api.v1.dto.UnlinkRequestV1;
import com.wa.banking.channel.api.v1.dto.VerifyIdentityRequestV1;
import com.wa.banking.channel.config.OpenApiExamples;
import com.wa.banking.channel.service.WhatsAppLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST v1 para vínculos de canal WhatsApp (F1–F4).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@RestController
@RequestMapping("/api/v1/channel-links")
@RequiredArgsConstructor
@Tag(name = "Channel Links V1", description = "Operaciones de vínculo WhatsApp - API v1 (F1–F4)")
public class WhatsAppLinkControllerV1 {

    private final WhatsAppLinkService linkService;

    @GetMapping("/by-phone/{phone}")
    @Operation(
            summary = "F1 - Consulta estado de vínculo por número",
            description = "Consulta anti-duplicado: retorna el vínculo existente para un número E.164."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Vínculo encontrado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LinkResponseV1.class),
                            examples = @ExampleObject(name = "Vínculo activo", value = OpenApiExamples.LINK_ACTIVE_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Número sin vínculo registrado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "No encontrado", value = OpenApiExamples.NOT_FOUND_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Error interno", value = OpenApiExamples.INTERNAL_ERROR)
                    )
            )
    })
    public LinkResponseV1 findByPhone(
            @Parameter(description = "Número WhatsApp en formato E.164", example = "+541112345678")
            @PathVariable String phone) {
        return linkService.findByPhone(phone);
    }

    @PostMapping
    @Operation(
            summary = "F1 - Iniciar vínculo para número no vinculado",
            description = "Crea o reutiliza un registro de vínculo en estado NO_LINK. Rechaza números ya activos (RN1)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Vínculo iniciado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LinkResponseV1.class),
                            examples = @ExampleObject(name = "Vínculo iniciado", value = OpenApiExamples.LINK_NO_LINK_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validación fallida o regla de negocio",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Error de validación", value = OpenApiExamples.VALIDATION_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Número ya vinculado a cliente activo",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Duplicado activo", value = OpenApiExamples.DUPLICATE_LINK_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Error interno", value = OpenApiExamples.INTERNAL_ERROR)
                    )
            )
    })
    public ResponseEntity<LinkResponseV1> initiateLink(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Número WhatsApp a vincular",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = InitiateLinkRequestV1.class),
                            examples = @ExampleObject(name = "Iniciar vínculo", value = OpenApiExamples.INITIATE_LINK_REQUEST)
                    )
            )
            @Valid @RequestBody InitiateLinkRequestV1 request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(linkService.initiateLink(request));
    }

    @PostMapping("/{id}/accept-terms")
    @Operation(
            summary = "F1 - Aceptar términos",
            description = "Transición NO_LINK → PENDING_VERIFICATION tras aceptación explícita de términos."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Términos aceptados",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LinkResponseV1.class),
                            examples = @ExampleObject(
                                    name = "Pendiente verificación",
                                    value = OpenApiExamples.LINK_PENDING_VERIFICATION_RESPONSE
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validación fallida o estado inválido",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Estado inválido", value = OpenApiExamples.INVALID_STATE_ERROR),
                                    @ExampleObject(name = "Error de validación", value = OpenApiExamples.VALIDATION_ERROR)
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Vínculo no encontrado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "No encontrado", value = OpenApiExamples.NOT_FOUND_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Error interno", value = OpenApiExamples.INTERNAL_ERROR)
                    )
            )
    })
    public LinkResponseV1 acceptTerms(
            @Parameter(description = "Identificador del vínculo", example = "665f1a2b3c4d5e6f7a8b9c0d")
            @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Confirmación de aceptación de términos",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AcceptTermsRequestV1.class),
                            examples = @ExampleObject(name = "Aceptar términos", value = OpenApiExamples.ACCEPT_TERMS_REQUEST)
                    )
            )
            @Valid @RequestBody AcceptTermsRequestV1 request) {
        return linkService.acceptTerms(id, request);
    }

    @PostMapping("/{id}/verify")
    @Operation(
            summary = "F1 - Verificar identidad y consultar H1",
            description = "Valida documento contra H1 (users-service), OTP MVP y marca identidad verificada."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Verificación procesada (éxito o intento fallido)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LinkResponseV1.class),
                            examples = @ExampleObject(name = "Identidad verificada", value = OpenApiExamples.LINK_ACTIVE_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validación, usuario no elegible o estado inválido",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Estado inválido", value = OpenApiExamples.INVALID_STATE_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Vínculo no encontrado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "No encontrado", value = OpenApiExamples.NOT_FOUND_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Error interno", value = OpenApiExamples.INTERNAL_ERROR)
                    )
            )
    })
    public LinkResponseV1 verifyIdentity(
            @Parameter(description = "Identificador del vínculo", example = "665f1a2b3c4d5e6f7a8b9c0d")
            @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Datos de verificación de identidad",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = VerifyIdentityRequestV1.class),
                            examples = @ExampleObject(name = "Verificar identidad", value = OpenApiExamples.VERIFY_IDENTITY_REQUEST)
                    )
            )
            @Valid @RequestBody VerifyIdentityRequestV1 request) {
        return linkService.verifyIdentity(id, request);
    }

    @PostMapping("/{id}/complete-onboarding")
    @Operation(
            summary = "F1 - Completar onboarding",
            description = "Transición a ACTIVE y emisión de credencial de sesión H2 (stub configurable)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Onboarding completado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LinkResponseV1.class),
                            examples = @ExampleObject(name = "Vínculo activo", value = OpenApiExamples.LINK_ACTIVE_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Estado inválido para completar onboarding",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Estado inválido", value = OpenApiExamples.INVALID_STATE_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Vínculo no encontrado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "No encontrado", value = OpenApiExamples.NOT_FOUND_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Error interno", value = OpenApiExamples.INTERNAL_ERROR)
                    )
            )
    })
    public LinkResponseV1 completeOnboarding(
            @Parameter(description = "Identificador del vínculo", example = "665f1a2b3c4d5e6f7a8b9c0d")
            @PathVariable String id) {
        return linkService.completeOnboarding(id);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Consulta estado del vínculo por ID",
            description = "Obtiene el estado operativo completo del vínculo por identificador interno."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Vínculo encontrado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LinkResponseV1.class),
                            examples = @ExampleObject(name = "Vínculo activo", value = OpenApiExamples.LINK_ACTIVE_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Vínculo no encontrado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "No encontrado", value = OpenApiExamples.NOT_FOUND_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Error interno", value = OpenApiExamples.INTERNAL_ERROR)
                    )
            )
    })
    public LinkResponseV1 findById(
            @Parameter(description = "Identificador del vínculo", example = "665f1a2b3c4d5e6f7a8b9c0d")
            @PathVariable String id) {
        return linkService.findById(id);
    }

    @GetMapping("/{id}/profile")
    @Operation(
            summary = "F2 - Perfil del canal",
            description = "Perfil enmascarado sin datos legales sensibles (RN6)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Perfil del canal",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProfileResponseV1.class),
                            examples = @ExampleObject(name = "Perfil enmascarado", value = OpenApiExamples.PROFILE_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Vínculo no encontrado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "No encontrado", value = OpenApiExamples.NOT_FOUND_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Error interno", value = OpenApiExamples.INTERNAL_ERROR)
                    )
            )
    })
    public ProfileResponseV1 getProfile(
            @Parameter(description = "Identificador del vínculo", example = "665f1a2b3c4d5e6f7a8b9c0d")
            @PathVariable String id) {
        return linkService.getProfile(id);
    }

    @GetMapping("/{id}/preferences")
    @Operation(
            summary = "F2 - Consultar preferencias del canal",
            description = "Retorna idioma, notificaciones y horario silencioso del vínculo activo."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Preferencias del canal",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PreferencesResponseV1.class),
                            examples = @ExampleObject(name = "Preferencias", value = OpenApiExamples.PREFERENCES_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Vínculo no encontrado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "No encontrado", value = OpenApiExamples.NOT_FOUND_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Error interno", value = OpenApiExamples.INTERNAL_ERROR)
                    )
            )
    })
    public PreferencesResponseV1 getPreferences(
            @Parameter(description = "Identificador del vínculo", example = "665f1a2b3c4d5e6f7a8b9c0d")
            @PathVariable String id) {
        return linkService.getPreferences(id);
    }

    @PatchMapping("/{id}/preferences")
    @Operation(
            summary = "F2 - Actualizar preferencias del canal",
            description = "Actualización parcial de preferencias (idioma, notificaciones, quiet hours)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Preferencias actualizadas",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PreferencesResponseV1.class),
                            examples = @ExampleObject(name = "Preferencias", value = OpenApiExamples.PREFERENCES_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validación fallida",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Error de validación", value = OpenApiExamples.VALIDATION_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Vínculo no encontrado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "No encontrado", value = OpenApiExamples.NOT_FOUND_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Error interno", value = OpenApiExamples.INTERNAL_ERROR)
                    )
            )
    })
    public PreferencesResponseV1 updatePreferences(
            @Parameter(description = "Identificador del vínculo", example = "665f1a2b3c4d5e6f7a8b9c0d")
            @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Campos de preferencias a actualizar",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PreferencesRequestV1.class),
                            examples = @ExampleObject(name = "Actualizar preferencias", value = OpenApiExamples.PREFERENCES_REQUEST)
                    )
            )
            @Valid @RequestBody PreferencesRequestV1 request) {
        return linkService.updatePreferences(id, request);
    }

    @PostMapping("/{id}/block")
    @Operation(
            summary = "F3 - Bloquear vínculo",
            description = "Bloquea el vínculo activo y revoca la credencial de sesión H2 (RN5)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Vínculo bloqueado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LinkResponseV1.class),
                            examples = @ExampleObject(name = "Vínculo bloqueado", value = OpenApiExamples.LINK_BLOCKED_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validación fallida o estado inválido",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Estado inválido", value = OpenApiExamples.INVALID_STATE_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Vínculo no encontrado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "No encontrado", value = OpenApiExamples.NOT_FOUND_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Error interno", value = OpenApiExamples.INTERNAL_ERROR)
                    )
            )
    })
    public LinkResponseV1 block(
            @Parameter(description = "Identificador del vínculo", example = "665f1a2b3c4d5e6f7a8b9c0d")
            @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Confirmación explícita de bloqueo",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = BlockLinkRequestV1.class),
                            examples = @ExampleObject(name = "Confirmar bloqueo", value = OpenApiExamples.BLOCK_LINK_REQUEST)
                    )
            )
            @Valid @RequestBody BlockLinkRequestV1 request) {
        return linkService.block(id, request);
    }

    @PostMapping("/{id}/unlink")
    @Operation(
            summary = "F3 - Desvincular número",
            description = "Desvincula el número WhatsApp y revoca credencial H2. Estado final UNLINKED."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Número desvinculado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LinkResponseV1.class),
                            examples = @ExampleObject(name = "Vínculo activo previo", value = OpenApiExamples.LINK_ACTIVE_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validación fallida o estado inválido",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Estado inválido", value = OpenApiExamples.INVALID_STATE_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Vínculo no encontrado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "No encontrado", value = OpenApiExamples.NOT_FOUND_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Error interno", value = OpenApiExamples.INTERNAL_ERROR)
                    )
            )
    })
    public LinkResponseV1 unlink(
            @Parameter(description = "Identificador del vínculo", example = "665f1a2b3c4d5e6f7a8b9c0d")
            @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Confirmación explícita de desvinculación",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UnlinkRequestV1.class),
                            examples = @ExampleObject(name = "Confirmar desvinculación", value = OpenApiExamples.UNLINK_REQUEST)
                    )
            )
            @Valid @RequestBody UnlinkRequestV1 request) {
        return linkService.unlink(id, request);
    }

    @PostMapping("/{id}/relink")
    @Operation(
            summary = "F4 - Re-vinculación",
            description = "Inicia re-vinculación tras UNLINKED; exige nueva verificación completa (RN7)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Re-vinculación iniciada",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LinkResponseV1.class),
                            examples = @ExampleObject(name = "Pendiente verificación", value = OpenApiExamples.LINK_PENDING_VERIFICATION_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validación fallida o estado inválido",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Estado inválido", value = OpenApiExamples.INVALID_STATE_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Vínculo no encontrado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "No encontrado", value = OpenApiExamples.NOT_FOUND_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Error interno", value = OpenApiExamples.INTERNAL_ERROR)
                    )
            )
    })
    public LinkResponseV1 relink(
            @Parameter(description = "Identificador del vínculo", example = "665f1a2b3c4d5e6f7a8b9c0d")
            @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Confirmación de inicio de re-vinculación",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RelinkRequestV1.class),
                            examples = @ExampleObject(name = "Confirmar re-vinculación", value = OpenApiExamples.RELINK_REQUEST)
                    )
            )
            @Valid @RequestBody RelinkRequestV1 request) {
        return linkService.relink(id, request);
    }

    @GetMapping("/{id}/history")
    @Operation(
            summary = "Historial de interacciones para soporte",
            description = "Historial resumido paginado sin datos sensibles para equipos de soporte."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Página de historial",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = InteractionHistoryItemV1.class),
                            examples = @ExampleObject(name = "Historial paginado", value = OpenApiExamples.HISTORY_PAGE_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Vínculo no encontrado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "No encontrado", value = OpenApiExamples.NOT_FOUND_ERROR)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Error interno", value = OpenApiExamples.INTERNAL_ERROR)
                    )
            )
    })
    public Page<InteractionHistoryItemV1> getHistory(
            @Parameter(description = "Identificador del vínculo", example = "665f1a2b3c4d5e6f7a8b9c0d")
            @PathVariable String id,
            @PageableDefault(size = 20) Pageable pageable) {
        return linkService.getHistory(id, pageable);
    }
}
