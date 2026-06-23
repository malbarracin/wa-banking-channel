package com.wa.banking.auth.api.v1.controller;

import com.wa.banking.auth.api.error.ErrorResponse;
import com.wa.banking.auth.api.v1.dto.AuditEntryResponse;
import com.wa.banking.auth.api.v1.dto.CredentialStatusResponse;
import com.wa.banking.auth.api.v1.dto.IssueCredentialRequest;
import com.wa.banking.auth.api.v1.dto.IssueCredentialResponse;
import com.wa.banking.auth.api.v1.dto.RenewCredentialResponse;
import com.wa.banking.auth.api.v1.dto.RevokeByUserRequest;
import com.wa.banking.auth.api.v1.dto.RevokeCredentialRequest;
import com.wa.banking.auth.api.v1.dto.ValidateCredentialRequest;
import com.wa.banking.auth.api.v1.dto.ValidateCredentialResponse;
import com.wa.banking.auth.entity.AuditActor;
import com.wa.banking.auth.entity.RevokeReason;
import com.wa.banking.auth.service.SessionCredentialService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST v1 para credenciales de sesión del canal (H2).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@RestController
@RequestMapping("/api/v1/sessions/credentials")
@Tag(name = "Session Credentials", description = "Gestión de credenciales de sesión del canal WhatsApp (H2)")
@RequiredArgsConstructor
public class SessionCredentialControllerV1 {

    private final SessionCredentialService sessionCredentialService;

    @PostMapping
    @Operation(
            summary = "Emitir credencial de sesión tras verificación H3 (A1)",
            description = "Emite una credencial opaca para el vínculo canal-cliente. Revoca la credencial "
                    + "activa previa del mismo linkId. El token solo se devuelve en esta respuesta."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Credencial emitida",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IssueCredentialResponse.class),
                            examples = @ExampleObject(
                                    name = "Credencial emitida",
                                    value = """
                                            {
                                              "credentialId": "cred_abc123",
                                              "token": "sess_xxx_placeholder",
                                              "expiresAt": "2026-06-24T10:00:00Z"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validación fallida o identidad no verificada",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Error de validación",
                                            value = """
                                                    {
                                                      "code": "VALIDATION_ERROR",
                                                      "message": "Validation failed",
                                                      "details": ["channelLinkId: channelLinkId is required"],
                                                      "timestamp": "2026-06-23T10:30:00Z"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Identidad no verificada",
                                            value = """
                                                    {
                                                      "code": "BAD_REQUEST",
                                                      "message": "Identity verification is required before issuing a session credential",
                                                      "details": [],
                                                      "timestamp": "2026-06-23T10:30:00Z"
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Error interno",
                                    value = """
                                            {
                                              "code": "INTERNAL_ERROR",
                                              "message": "An unexpected error occurred",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<IssueCredentialResponse> issue(@Valid @RequestBody IssueCredentialRequest request) {
        IssueCredentialResponse response = sessionCredentialService.issue(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/validate")
    @Operation(
            summary = "Validar credencial para servicios de productos H4–H6",
            description = "Verifica que la credencial exista, esté activa, no expirada y que el token coincida. "
                    + "Retorna valid=false sin error HTTP cuando la credencial es inválida."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Resultado de validación",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ValidateCredentialResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Credencial válida",
                                            value = """
                                                    {
                                                      "valid": true,
                                                      "bankUserId": "user-xyz789",
                                                      "channelLinkId": "link-abc123",
                                                      "expiresAt": "2026-06-24T10:00:00Z"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Credencial inválida",
                                            value = """
                                                    {
                                                      "valid": false,
                                                      "bankUserId": null,
                                                      "channelLinkId": null,
                                                      "expiresAt": null
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validación fallida del request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Error de validación",
                                    value = """
                                            {
                                              "code": "VALIDATION_ERROR",
                                              "message": "Validation failed",
                                              "details": ["token: token is required"],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Error interno",
                                    value = """
                                            {
                                              "code": "INTERNAL_ERROR",
                                              "message": "An unexpected error occurred",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<ValidateCredentialResponse> validate(
            @Valid @RequestBody ValidateCredentialRequest request) {
        return ResponseEntity.ok(sessionCredentialService.validate(request));
    }

    @PostMapping("/revoke-by-user")
    @Operation(
            summary = "Revocar todas las credenciales activas de un usuario bancario",
            description = "Revoca en bloque todas las credenciales ACTIVE del bankUserId indicado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Credenciales revocadas (puede ser 0)"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validación fallida",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Error de validación",
                                    value = """
                                            {
                                              "code": "VALIDATION_ERROR",
                                              "message": "Validation failed",
                                              "details": ["bankUserId: bankUserId is required"],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Error interno",
                                    value = """
                                            {
                                              "code": "INTERNAL_ERROR",
                                              "message": "An unexpected error occurred",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<Void> revokeByUser(@Valid @RequestBody RevokeByUserRequest request) {
        sessionCredentialService.revokeByUser(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Consultar estado de credencial sin token",
            description = "Devuelve metadatos y estado de la credencial sin exponer el token opaco."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Estado de la credencial",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CredentialStatusResponse.class),
                            examples = @ExampleObject(
                                    name = "Credencial activa",
                                    value = """
                                            {
                                              "credentialId": "cred_abc123",
                                              "channelLinkId": "link-abc123",
                                              "bankUserId": "user-xyz789",
                                              "phoneNumber": "+541112345678",
                                              "status": "ACTIVE",
                                              "issuedAt": "2026-06-23T10:00:00Z",
                                              "expiresAt": "2026-06-24T10:00:00Z",
                                              "revokedAt": null,
                                              "revokeReason": null,
                                              "renewalCount": 0
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Credencial no encontrada",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "No encontrada",
                                    value = """
                                            {
                                              "code": "NOT_FOUND",
                                              "message": "Session credential not found: cred_xxx",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Error interno",
                                    value = """
                                            {
                                              "code": "INTERNAL_ERROR",
                                              "message": "An unexpected error occurred",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<CredentialStatusResponse> getStatus(
            @Parameter(description = "Identificador de la credencial", example = "cred_abc123")
            @PathVariable("id") String credentialId) {
        return ResponseEntity.ok(sessionCredentialService.getStatus(credentialId));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Revocar credencial inmediatamente (consumo H3 block/unlink)",
            description = "Revoca la credencial con motivo POLICY y actor CHANNEL. Usado por H3 en block/unlink. "
                    + "Idempotente: si la credencial ya está revocada, responde 204 sin error."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Credencial revocada (o ya estaba revocada)"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Credencial no encontrada",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "No encontrada",
                                    value = """
                                            {
                                              "code": "NOT_FOUND",
                                              "message": "Session credential not found: cred_xxx",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Error interno",
                                    value = """
                                            {
                                              "code": "INTERNAL_ERROR",
                                              "message": "An unexpected error occurred",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<Void> revoke(
            @Parameter(description = "Identificador de la credencial", example = "cred_abc123")
            @PathVariable("id") String credentialId) {
        sessionCredentialService.revoke(credentialId, RevokeReason.POLICY, AuditActor.CHANNEL);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/renew")
    @Operation(
            summary = "Renovar credencial activa extendiendo TTL (A2)",
            description = "Extiende la fecha de expiración de una credencial ACTIVE según session.credential.ttl-hours."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Credencial renovada",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RenewCredentialResponse.class),
                            examples = @ExampleObject(
                                    name = "Renovación exitosa",
                                    value = """
                                            {
                                              "credentialId": "cred_abc123",
                                              "expiresAt": "2026-06-25T10:00:00Z",
                                              "renewalCount": 1
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Credencial no encontrada",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "No encontrada",
                                    value = """
                                            {
                                              "code": "NOT_FOUND",
                                              "message": "Session credential not found: cred_xxx",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Credencial no renovable",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "No activa",
                                    value = """
                                            {
                                              "code": "BAD_REQUEST",
                                              "message": "Session credential is not active",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Error interno",
                                    value = """
                                            {
                                              "code": "INTERNAL_ERROR",
                                              "message": "An unexpected error occurred",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<RenewCredentialResponse> renew(
            @Parameter(description = "Identificador de la credencial", example = "cred_abc123")
            @PathVariable("id") String credentialId) {
        return ResponseEntity.ok(sessionCredentialService.renew(credentialId));
    }

    @PostMapping("/{id}/revoke")
    @Operation(
            summary = "Revocar credencial con motivo explícito (banco/riesgo)",
            description = "Revoca la credencial con motivo configurable. Actor de auditoría: BANK."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Credencial revocada"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Credencial no encontrada",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "No encontrada",
                                    value = """
                                            {
                                              "code": "NOT_FOUND",
                                              "message": "Session credential not found: cred_xxx",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Credencial ya revocada",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Ya revocada",
                                    value = """
                                            {
                                              "code": "BAD_REQUEST",
                                              "message": "Session credential is already revoked",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Error interno",
                                    value = """
                                            {
                                              "code": "INTERNAL_ERROR",
                                              "message": "An unexpected error occurred",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<Void> revokeWithReason(
            @Parameter(description = "Identificador de la credencial", example = "cred_abc123")
            @PathVariable("id") String credentialId,
            @RequestBody(required = false) RevokeCredentialRequest request) {
        RevokeCredentialRequest revokeRequest = request != null ? request : new RevokeCredentialRequest();
        sessionCredentialService.revokeWithReason(credentialId, revokeRequest, AuditActor.BANK);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/audit")
    @Operation(
            summary = "Historial paginado de auditoría de la credencial",
            description = "Lista entradas de auditoría ordenadas por fecha descendente. Parámetros: page, size, sort."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Página de entradas de auditoría",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuditEntryResponse.class),
                            examples = @ExampleObject(
                                    name = "Historial paginado",
                                    value = """
                                            {
                                              "content": [
                                                {
                                                  "credentialId": "cred_abc123",
                                                  "channelLinkId": "link-abc123",
                                                  "bankUserId": "user-xyz789",
                                                  "action": "ISSUED",
                                                  "actor": "CHANNEL",
                                                  "reason": null,
                                                  "performedAt": "2026-06-23T10:00:00Z"
                                                }
                                              ],
                                              "totalElements": 1,
                                              "totalPages": 1,
                                              "size": 20,
                                              "number": 0
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Credencial no encontrada",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "No encontrada",
                                    value = """
                                            {
                                              "code": "NOT_FOUND",
                                              "message": "Session credential not found: cred_xxx",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Error interno",
                                    value = """
                                            {
                                              "code": "INTERNAL_ERROR",
                                              "message": "An unexpected error occurred",
                                              "details": [],
                                              "timestamp": "2026-06-23T10:30:00Z"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<Page<AuditEntryResponse>> getAuditHistory(
            @Parameter(description = "Identificador de la credencial", example = "cred_abc123")
            @PathVariable("id") String credentialId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(sessionCredentialService.getAuditHistory(credentialId, pageable));
    }
}
