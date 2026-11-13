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
 * API v1 de usuarios: alta, consulta por vistas, actualización parcial y cambio de estado.
 * La proyección de consulta usa {@code X-Query-Actor}; {@code X-Actor-Id} solo audita.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Gestión de usuarios bancarios (H1) — flujos U1–U4")
public class UserController {

    private static final String HEADER_ACTOR = "X-Actor-Id";
    private static final String HEADER_QUERY_ACTOR = "X-Query-Actor";
    private static final String MEDIA_JSON = "application/json";

    private static final String EX_CREATE_BODY = """
            {
              "documentType": "DNI",
              "documentNumber": "30111222",
              "firstName": "Maria",
              "lastName": "Lopez",
              "email": "maria.lopez@example.com",
              "phone": "+5491112345678",
              "address": "Av. Corrientes 1234",
              "birthDate": "1990-05-20",
              "preferredName": "Mari",
              "preferredLanguage": "es"
            }
            """;

    private static final String EX_USER_ACTIVE = """
            {
              "id": "661f0a1b2c3d4e5f67890123",
              "documentType": "DNI",
              "documentNumber": "30111222",
              "firstName": "Maria",
              "lastName": "Lopez",
              "email": "maria.lopez@example.com",
              "phone": "+5491112345678",
              "address": "Av. Corrientes 1234",
              "birthDate": "1990-05-20",
              "preferredName": "Mari",
              "preferredLanguage": "es",
              "status": "ACTIVE",
              "channelLinkEligible": true,
              "createdAt": "2026-10-19T12:00:00Z",
              "updatedAt": "2026-10-19T12:00:00Z"
            }
            """;

    private static final String EX_USER_BASIC = """
            {
              "id": "661f0a1b2c3d4e5f67890123",
              "documentType": "DNI",
              "documentNumber": "30111222",
              "status": "ACTIVE",
              "channelLinkEligible": true
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
              "address": "Av. Corrientes 1234",
              "birthDate": "1990-05-20",
              "preferredName": "Mari",
              "preferredLanguage": "es",
              "status": "SUSPENDED",
              "channelLinkEligible": false,
              "createdAt": "2026-10-19T12:00:00Z",
              "updatedAt": "2026-10-19T14:00:00Z"
            }
            """;

    private static final String EX_UPDATE_BODY = """
            {
              "preferredName": "Mari",
              "email": "mari@example.com",
              "phone": "+5491112345678",
              "preferredLanguage": "es",
              "address": "Av. Corrientes 1234"
            }
            """;

    private static final String EX_STATUS_BODY = """
            {
              "status": "SUSPENDED"
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
              "message": "Invalid request parameters",
              "details": [],
              "timestamp": "2026-10-19T12:00:00Z"
            }
            """;

    private static final String EX_NON_UPDATABLE = """
            {
              "code": "BAD_REQUEST",
              "message": "Field is not updatable in this service",
              "details": [
                { "field": "documentNumber", "message": "Field is not updatable in this service" }
              ],
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

    private static final String EX_DUPLICATE_DOCUMENT = """
            {
              "code": "CONFLICT",
              "message": "A user with the same document already exists",
              "details": [
                { "field": "documentNumber", "message": "Document already registered" }
              ],
              "timestamp": "2026-10-19T12:00:00Z"
            }
            """;

    private static final String EX_DUPLICATE_EMAIL = """
            {
              "code": "CONFLICT",
              "message": "A user with the same email already exists",
              "details": [
                { "field": "email", "message": "Email already registered" }
              ],
              "timestamp": "2026-10-19T12:00:00Z"
            }
            """;

    private static final String EX_INVALID_TRANSITION = """
            {
              "code": "CONFLICT",
              "message": "Invalid status transition from ACTIVE to ACTIVE",
              "details": [
                { "field": "status", "message": "Status transition is not allowed" }
              ],
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
     * Alta de usuario con datos Compliance; estado inicial ACTIVE y unicidad de documento y email.
     *
     * @param request datos mínimos obligatorios
     * @param actor   referencia opcional de auditoría ({@code X-Actor-Id})
     * @return 201 con el usuario creado
     */
    @PostMapping
    @Operation(
            summary = "Alta de usuario (U1)",
            description = "Crea usuario ACTIVE con channelLinkEligible=true. "
                    + "Unicidad independiente de (documentType, documentNumber) y de email. "
                    + "Campos Compliance: documentType, documentNumber, firstName, lastName, email, "
                    + "phone, address, birthDate.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = UserCreateRequest.class),
                            examples = {
                                    @ExampleObject(name = "DNI", summary = "Alta con DNI", value = EX_CREATE_BODY),
                                    @ExampleObject(
                                            name = "Pasaporte",
                                            summary = "Alta con pasaporte",
                                            value = """
                                                    {
                                                      "documentType": "PASSPORT",
                                                      "documentNumber": "AAB123456",
                                                      "firstName": "Maria",
                                                      "lastName": "Lopez",
                                                      "email": "maria.passport@example.com",
                                                      "phone": "+5491112345678",
                                                      "address": "Av. Corrientes 1234",
                                                      "birthDate": "1990-05-20"
                                                    }
                                                    """)
                            })))
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
                    description = "Validación Bean Validation o cuerpo inválido",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "validation", value = EX_VALIDATION_ERROR),
                                    @ExampleObject(name = "badRequest", value = EX_BAD_REQUEST)
                            })),
            @ApiResponse(
                    responseCode = "409",
                    description = "Documento o email duplicado (code=CONFLICT)",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "duplicateDocument", value = EX_DUPLICATE_DOCUMENT),
                                    @ExampleObject(name = "duplicateEmail", value = EX_DUPLICATE_EMAIL)
                            })),
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
     * Consulta por documento con proyección según {@code X-Query-Actor}.
     *
     * @param documentType   tipo de documento
     * @param documentNumber número de documento
     * @param queryActor     origen declarado de la consulta (no autentica)
     * @return 200 con vista completa o básica
     */
    @GetMapping(params = {"documentType", "documentNumber"})
    @Operation(
            summary = "Consulta por documento (U2)",
            description = "Busca por documentType + documentNumber. "
                    + "Vista completa para BUSINESS_OPERATIONS/SUPPORT (salvo restricción de "
                    + "users.query.full-view-actors); vista básica para BANK_SERVICE, ausente o desconocido.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Usuario encontrado; vista completa o básica según X-Query-Actor",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(oneOf = {UserResponse.class, UserBasicResponse.class}),
                            examples = {
                                    @ExampleObject(name = "fullView", value = EX_USER_ACTIVE),
                                    @ExampleObject(name = "basicView", value = EX_USER_BASIC)
                            })),
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
                            examples = @ExampleObject(name = "notFound", value = EX_NOT_FOUND))),
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
            @Parameter(
                    description = "Origen declarado: BUSINESS_OPERATIONS, SUPPORT o BANK_SERVICE; "
                            + "ausente/desconocido recibe subset básico. No autentica.",
                    example = "BUSINESS_OPERATIONS")
            @RequestHeader(value = HEADER_QUERY_ACTOR, required = false) String queryActor) {
        if (documentNumber.isBlank()) {
            throw new IllegalArgumentException("documentNumber is required");
        }
        return ResponseEntity.ok(queryProjection.project(
                userService.findByDocument(documentType, documentNumber), queryActor));
    }

    /**
     * Consulta por id interno con proyección según {@code X-Query-Actor}.
     *
     * @param id         id interno
     * @param queryActor origen declarado de la consulta (no autentica)
     * @return 200 con vista completa o básica
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Consulta por id (U2)",
            description = "Devuelve datos autorizados + status + channelLinkEligible (true solo si ACTIVE). "
                    + "La proyección depende de X-Query-Actor; X-Actor-Id no afecta la vista.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Usuario encontrado; vista completa o básica según X-Query-Actor",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(oneOf = {UserResponse.class, UserBasicResponse.class}),
                            examples = {
                                    @ExampleObject(name = "fullView", value = EX_USER_ACTIVE),
                                    @ExampleObject(name = "basicView", value = EX_USER_BASIC)
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
    public ResponseEntity<?> findById(
            @Parameter(description = "Id interno del usuario", example = "661f0a1b2c3d4e5f67890123")
            @PathVariable String id,
            @Parameter(
                    description = "Origen declarado: BUSINESS_OPERATIONS, SUPPORT o BANK_SERVICE; "
                            + "ausente/desconocido recibe subset básico. No autentica.",
                    example = "BANK_SERVICE")
            @RequestHeader(value = HEADER_QUERY_ACTOR, required = false) String queryActor) {
        if (id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        return ResponseEntity.ok(queryProjection.project(userService.findById(id), queryActor));
    }

    /**
     * Actualización parcial de campos de contacto y tratamiento; rechaza campos ajenos al contrato.
     *
     * @param id      id interno
     * @param request campos permitidos (al menos uno)
     * @param actor   referencia opcional de auditoría
     * @return 200 con el usuario actualizado
     */
    @PatchMapping("/{id}")
    @Operation(
            summary = "Actualizar campos permitidos (U3)",
            description = "Solo preferredName, email, phone, preferredLanguage y address. "
                    + "Documento, nombre/apellido legales, birthDate y status no son actualizables. "
                    + "Email duplicado → 409 CONFLICT. Audita el cambio.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = UserUpdateRequest.class),
                            examples = @ExampleObject(name = "contacto", value = EX_UPDATE_BODY))))
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
                    description = "Sin campos, validación o campo no actualizable",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "emptyUpdate", value = EX_BAD_REQUEST),
                                    @ExampleObject(name = "validation", value = EX_VALIDATION_ERROR),
                                    @ExampleObject(name = "nonUpdatable", value = EX_NON_UPDATABLE)
                            })),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "notFound", value = EX_NOT_FOUND))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Email duplicado (code=CONFLICT)",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "duplicateEmail", value = EX_DUPLICATE_EMAIL))),
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
     * Cambia el estado operativo validando la matriz de transiciones; la baja lógica es terminal.
     *
     * @param id      id interno
     * @param request estado destino
     * @param actor   referencia opcional de auditoría
     * @return 200 con el usuario en el nuevo estado
     */
    @PatchMapping("/{id}/status")
    @Operation(
            summary = "Cambiar estado (U4)",
            description = "Transiciones: ACTIVE↔SUSPENDED; ACTIVE|SUSPENDED→LOGICALLY_DELETED. "
                    + "LOGICALLY_DELETED no admite salida. Mismo estado o transición inválida → "
                    + "auditoría FAILURE + 409 CONFLICT. channelLinkEligible=false si status≠ACTIVE.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = UserStatusChangeRequest.class),
                            examples = {
                                    @ExampleObject(name = "suspender", value = EX_STATUS_BODY),
                                    @ExampleObject(
                                            name = "reactivar",
                                            value = """
                                                    { "status": "ACTIVE" }
                                                    """),
                                    @ExampleObject(
                                            name = "bajaLogica",
                                            value = """
                                                    { "status": "LOGICALLY_DELETED" }
                                                    """)
                            })))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Estado actualizado",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(name = "suspended", value = EX_USER_SUSPENDED))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Cuerpo inválido",
                    content = @Content(
                            mediaType = MEDIA_JSON,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "validation", value = EX_VALIDATION_ERROR))),
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
     * Devuelve el historial append-only de auditoría del usuario, ordenado descendente por timestamp.
     *
     * @param id id interno
     * @return 200 con la lista de eventos
     */
    @GetMapping("/{id}/audit")
    @Operation(
            summary = "Historial de auditoría",
            description = "Eventos append-only ordenados desc por timestamp "
                    + "(USER_CREATED, USER_UPDATED, STATUS_CHANGED; SUCCESS/FAILURE).")
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
