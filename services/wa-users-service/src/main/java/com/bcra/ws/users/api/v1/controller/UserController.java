package com.bcra.ws.users.api.v1.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.ws.users.api.v1.dto.UserAuditEventResponse;
import com.bcra.ws.users.api.v1.dto.UserBasicResponse;
import com.bcra.ws.users.api.v1.dto.UserCreateRequest;
import com.bcra.ws.users.api.v1.dto.UserResponse;
import com.bcra.ws.users.api.v1.dto.UserStatusChangeRequest;
import com.bcra.ws.users.api.v1.dto.UserUpdateRequest;
import com.bcra.ws.users.api.v1.mapper.UserQueryProjection;
import com.bcra.ws.users.domain.DocumentType;
import com.bcra.ws.users.exception.ErrorResponse;
import com.bcra.ws.users.service.UserService;

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

/**
 * API v1 de usuarios: alta, consulta, actualización y cambio de estado (U1–U4).
 *
 * @since 2026-10-19
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Gestión de usuarios bancarios (H1) — flujos U1–U4")
public class UserController {

    private static final String HEADER_ACTOR = "X-Actor-Id";
    private static final String MEDIA_JSON = "application/json";

    private static final String EX_USER_ACTIVE = """
            {
              "id": "661f0a1b2c3d4e5f67890123",
              "documentType": "DNI",
              "documentNumber": "30111222",
              "firstName": "Maria",
              "lastName": "Lopez",
              "email": "maria.lopez@example.com",
              "phone": "+5491112345678",
              "preferredName": "Mari",
              "preferredLanguage": "es",
              "status": "ACTIVE",
              "channelLinkEligible": true,
              "createdAt": "2026-10-19T12:00:00Z",
              "updatedAt": "2026-10-19T12:00:00Z"
            }
            """;

    private static final String EX_USER_SUSPENDED = """
            {
              "id": "661f0a1b2c3d4e5f67890123",
              "documentType": "DNI",
              "documentNumber": "30111222",
              "firstName": "Maria",
              "lastName": "Lopez",
              "email": "maria.lopez@example.com",
              "phone": "+5491112345678",
              "preferredName": "Mari",
              "preferredLanguage": "es",
              "status": "SUSPENDED",
              "channelLinkEligible": false,
              "createdAt": "2026-10-19T12:00:00Z",
              "updatedAt": "2026-10-19T14:00:00Z"
            }
            """;

    private static final String EX_VALIDATION_ERROR = """
            {
              "code": "VALIDATION_ERROR",
              "message": "Request validation failed",
              "details": [
                { "field": "email", "message": "email must be valid" }
              ],
              "timestamp": "2026-10-19T12:00:00Z"
            }
            """;

    private static final String EX_BAD_REQUEST = """
            {
              "code": "BAD_REQUEST",
              "message": "At least one updatable field is required: preferredName, email, phone, preferredLanguage",
              "details": [],
              "timestamp": "2026-10-19T12:00:00Z"
            }
            """;

    private static final String EX_NOT_FOUND = """
            {
              "code": "NOT_FOUND",
              "message": "User not found",
              "details": [],
              "timestamp": "2026-10-19T12:00:00Z"
            }
            """;

    private static final String EX_DUPLICATE = """
            {
              "code": "CONFLICT",
              "message": "A user with the same document already exists",
              "details": [
                {
                  "field": "documentNumber",
                  "message": "Document already registered"
                }
              ],
              "timestamp": "2026-10-19T12:00:00Z"
            }
            """;

    private static final String EX_INVALID_TRANSITION = """
            {
              "code": "CONFLICT",
              "message": "Invalid status transition from ACTIVE to ACTIVE",
              "details": [{ "field": "status", "message": "Status transition is not allowed" }],
              "timestamp": "2026-10-19T12:00:00Z"
            }
            """;

    private static final String EX_INTERNAL = """
            {
              "code": "INTERNAL_ERROR",
              "message": "An unexpected error occurred",
              "details": [],
              "timestamp": "2026-10-19T12:00:00Z"
            }
            """;

    private static final String EX_AUDIT_TRAIL = """
            [
              {
                "id": "66f0a1b2c3d4e5f678901234",
                "userId": "661f0a1b2c3d4e5f67890123",
                "action": "STATUS_CHANGED",
                "timestamp": "2026-10-19T14:00:00Z",
                "result": "SUCCESS",
                "summary": { "from": "ACTIVE", "to": "SUSPENDED" },
                "actor": "ops"
              },
              {
                "id": "66f0a1b2c3d4e5f678901233",
                "userId": "661f0a1b2c3d4e5f67890123",
                "action": "USER_CREATED",
                "timestamp": "2026-10-19T12:00:00Z",
                "result": "SUCCESS",
                "summary": { "documentType": "DNI", "documentNumber": "30111222" },
                "actor": "ops"
              }
            ]
            """;

    private final UserService userService;
    private final UserQueryProjection queryProjection;

    /**
     * U1 — Alta de usuario. Estado inicial ACTIVE.
     *
     * @param request datos mínimos
     * @param actor   actor opcional
     * @return 201 con usuario creado
     */
    @PostMapping
    @Operation(
            summary = "Alta de usuario (U1)",
            description = "Crea usuario con estado inicial ACTIVE y channelLinkEligible=true. "
                    + "Unicidad por (documentType, documentNumber). Duplicado → 409.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Usuario creado",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(name = "created", value = EX_USER_ACTIVE))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validación Bean Validation",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "validation", value = EX_VALIDATION_ERROR))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Documento duplicado (code=CONFLICT)",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "duplicate", value = EX_DUPLICATE))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "internal", value = EX_INTERNAL)))
    })
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody UserCreateRequest request,
            @Parameter(description = "Identificador opcional del actor (auditoría)", example = "ops")
            @RequestHeader(value = HEADER_ACTOR, required = false) String actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request, actor));
    }

    /**
     * U2 — Consulta por documento (query params). Debe declararse antes que {@code /{id}}
     * para que Spring distinga por presencia de query; aquí se usa un mapping dedicado
     * sin path variable conflictivo vía método separado con params requeridos.
     *
     * @param documentType   tipo
     * @param documentNumber número
     * @return 200 usuario
     */
    @GetMapping(params = {"documentType", "documentNumber"})
    @Operation(
            summary = "Consulta por documento (U2)",
            description = "Busca por documentType + documentNumber (clave de unicidad).")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Usuario encontrado; vista completa o básica según X-Query-Actor",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(oneOf = {UserResponse.class, UserBasicResponse.class}),
                            examples = @ExampleObject(name = "found", value = EX_USER_ACTIVE))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Parámetros faltantes o inválidos",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "badRequest", value = EX_BAD_REQUEST))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "notFound",
                                    value = """
                                            {
                                              "code": "NOT_FOUND",
                                              "message": "User not found",
                                              "details": [],
                                              "timestamp": "2026-10-19T12:00:00Z"
                                            }
                                            """))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "internal", value = EX_INTERNAL)))
    })
    public ResponseEntity<?> findByDocument(
            @Parameter(description = "Tipo de documento", example = "DNI")
            @RequestParam DocumentType documentType,
            @Parameter(description = "Número de documento", example = "30111222")
            @RequestParam String documentNumber,
            @Parameter(description = "Origen declarado: BUSINESS_OPERATIONS, SUPPORT o BANK_SERVICE; "
                    + "ausente/desconocido recibe subset básico. No autentica al solicitante.")
            @RequestHeader(value = "X-Query-Actor", required = false) String queryActor) {
        if (documentNumber.isBlank()) {
            throw new IllegalArgumentException("documentNumber is required");
        }
        return ResponseEntity.ok(queryProjection.project(
                userService.findByDocument(documentType, documentNumber), queryActor));
    }

    /**
     * U2 — Consulta por id interno.
     *
     * @param id id interno
     * @return 200 usuario
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Consulta por id (U2)",
            description = "Devuelve datos autorizados + status + channelLinkEligible (true solo si ACTIVE).")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Usuario encontrado; vista completa o básica según X-Query-Actor",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(oneOf = {UserResponse.class, UserBasicResponse.class}),
                            examples = @ExampleObject(name = "found", value = EX_USER_ACTIVE))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "notFound", value = EX_NOT_FOUND))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "internal", value = EX_INTERNAL)))
    })
    public ResponseEntity<?> findById(
            @Parameter(description = "Id interno del usuario", example = "661f0a1b2c3d4e5f67890123")
            @PathVariable String id,
            @Parameter(description = "Origen declarado: BUSINESS_OPERATIONS, SUPPORT o BANK_SERVICE; "
                    + "ausente/desconocido recibe subset básico. No autentica al solicitante.")
            @RequestHeader(value = "X-Query-Actor", required = false) String queryActor) {
        if (id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        return ResponseEntity.ok(queryProjection.project(userService.findById(id), queryActor));
    }

    /**
     * U3 — Actualización de campos permitidos.
     *
     * @param id      id interno
     * @param request campos
     * @param actor   actor opcional
     * @return 200 usuario actualizado
     */
    @PatchMapping("/{id}")
    @Operation(
            summary = "Actualizar campos permitidos (U3)",
            description = "Solo preferredName, email, phone, preferredLanguage. "
                    + "Documento e identidad legal no son actualizables. Audita el cambio.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Usuario actualizado",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(name = "updated", value = EX_USER_ACTIVE))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Sin campos / email inválido",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "emptyUpdate", value = EX_BAD_REQUEST),
                                    @ExampleObject(name = "validation", value = EX_VALIDATION_ERROR)
                            })),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "notFound", value = EX_NOT_FOUND))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "internal", value = EX_INTERNAL)))
    })
    public ResponseEntity<UserResponse> update(
            @Parameter(description = "Id interno del usuario", example = "661f0a1b2c3d4e5f67890123")
            @PathVariable String id,
            @Valid @RequestBody UserUpdateRequest request,
            @Parameter(description = "Identificador opcional del actor (auditoría)", example = "ops")
            @RequestHeader(value = HEADER_ACTOR, required = false) String actor) {
        return ResponseEntity.ok(userService.update(id, request, actor));
    }

    /**
     * U4 — Cambio de estado con baja lógica terminal y auditoría de éxitos y rechazos.
     *
     * @param id      id interno
     * @param request estado destino
     * @param actor   actor opcional
     * @return 200 usuario
     */
    @PatchMapping("/{id}/status")
    @Operation(
            summary = "Cambiar estado (U4)",
            description = "Transiciones: ACTIVE↔SUSPENDED, baja lógica (LOGICALLY_DELETED), "
                    + "sin salida desde LOGICALLY_DELETED. Audita éxitos y rechazos, incluido el mismo estado. "
                    + "channelLinkEligible=false si status≠ACTIVE.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Estado actualizado",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(name = "suspended", value = EX_USER_SUSPENDED))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Transición inválida",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "invalidTransition", value = EX_INVALID_TRANSITION))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "notFound", value = EX_NOT_FOUND))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "internal", value = EX_INTERNAL)))
    })
    public ResponseEntity<UserResponse> changeStatus(
            @Parameter(description = "Id interno del usuario", example = "661f0a1b2c3d4e5f67890123")
            @PathVariable String id,
            @Valid @RequestBody UserStatusChangeRequest request,
            @Parameter(description = "Identificador opcional del actor (auditoría)", example = "ops")
            @RequestHeader(value = HEADER_ACTOR, required = false) String actor) {
        return ResponseEntity.ok(userService.changeStatus(id, request, actor));
    }

    /**
     * Historial de auditoría del usuario.
     *
     * @param id id interno
     * @return 200 lista de eventos
     */
    @GetMapping("/{id}/audit")
    @Operation(
            summary = "Historial de auditoría",
            description = "Eventos append-only ordenados desc por timestamp (USER_CREATED, USER_UPDATED, STATUS_CHANGED).")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de eventos",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = UserAuditEventResponse.class),
                            examples = @ExampleObject(name = "trail", value = EX_AUDIT_TRAIL))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "notFound", value = EX_NOT_FOUND))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "internal", value = EX_INTERNAL)))
    })
    public ResponseEntity<List<UserAuditEventResponse>> findAuditTrail(
            @Parameter(description = "Id interno del usuario", example = "661f0a1b2c3d4e5f67890123")
            @PathVariable String id) {
        return ResponseEntity.ok(userService.findAuditTrail(id));
    }
}
