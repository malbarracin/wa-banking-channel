package com.bank.users.user.controller;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bank.users.exception.ErrorResponse;
import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.user.dto.ChangeUserStatusRequest;
import com.bank.users.user.dto.UpdateUserRequest;
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.dto.UserChannelEligibilityResponse;
import com.bank.users.user.service.UserService;

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
 * API REST de usuarios bancarios del canal WhatsApp (H1).
 * Expone alta, consulta, actualización de campos permitidos, cambio de estado y aptitud de vínculo.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Alta, consulta, actualización y estado de usuarios bancarios (H1)")
public class UserController {

    private static final String USER_ID_EXAMPLE = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    private static final String USER_RESPONSE_EXAMPLE = """
            {
              "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
              "tipoDocumento": "DNI",
              "numeroDocumento": "30111222",
              "nombreTratamiento": "Ana Pérez",
              "email": "ana.perez@example.com",
              "telefonoContacto": "+5491112345678",
              "preferencias": {"idioma": "es"},
              "status": "ACTIVO",
              "aptoParaVinculoCanal": true,
              "createdAt": "2026-10-15T12:00:00Z",
              "updatedAt": "2026-10-15T12:00:00Z",
              "historialResumido": [
                {
                  "occurredAt": "2026-10-15T12:00:00Z",
                  "action": "ALTA",
                  "result": "OK",
                  "detail": "OPERACION_CONFIRMADA"
                }
              ]
            }
            """;

    private final UserService service;

    /**
     * Consulta la aptitud de vínculo de canal para consumidores (p. ej. H3).
     * No expone datos personales ni historial de auditoría.
     */
    @GetMapping("/{id}/channel-eligibility")
    @Operation(
            summary = "Consultar aptitud de vínculo de canal",
            description = """
                    Devuelve identidad interna, estado y si el usuario puede iniciar un nuevo vínculo \
                    de canal. Solo ACTIVO es apto. Pensado para servicios consumidores (H3).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aptitud evaluada",
                    content = @Content(schema = @Schema(implementation = UserChannelEligibilityResponse.class),
                            examples = @ExampleObject(name = "ActivoApto", value = """
                                    {
                                      "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                                      "status": "ACTIVO",
                                      "aptoParaVinculoCanal": true
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "UUID inválido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "BadRequest", ref = "#/components/examples/BadRequest"))),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "NotFound", ref = "#/components/examples/NotFound"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InternalError", ref = "#/components/examples/InternalError")))
    })
    public UserChannelEligibilityResponse channelEligibility(
            @Parameter(description = "Identificador interno del usuario", example = USER_ID_EXAMPLE,
                    required = true)
            @PathVariable UUID id) {
        return service.channelEligibility(id);
    }

    /**
     * Cambia el estado operativo según la matriz H1 y audita el resultado.
     */
    @PatchMapping("/{id}/status")
    @Operation(
            summary = "Cambiar estado operativo",
            description = """
                    Aplica la matriz H1: ACTIVO↔SUSPENDIDO; ACTIVO|SUSPENDIDO→BAJA_LOGICA. \
                    Desde BAJA_LOGICA no se reactiva en este hito. Audita OK o RECHAZADO.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado",
                    content = @Content(schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(name = "Suspendido", value = """
                                    {
                                      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                                      "tipoDocumento": "DNI",
                                      "numeroDocumento": "30111222",
                                      "nombreTratamiento": "Ana Pérez",
                                      "email": "ana.perez@example.com",
                                      "telefonoContacto": "+5491112345678",
                                      "preferencias": {"idioma": "es"},
                                      "status": "SUSPENDIDO",
                                      "aptoParaVinculoCanal": false,
                                      "createdAt": "2026-10-15T12:00:00Z",
                                      "updatedAt": "2026-10-15T12:05:00Z",
                                      "historialResumido": [
                                        {
                                          "occurredAt": "2026-10-15T12:05:00Z",
                                          "action": "CAMBIO_ESTADO",
                                          "result": "OK",
                                          "detail": "OPERACION_CONFIRMADA"
                                        }
                                      ]
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "Body o estado inválido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Validacion", ref = "#/components/examples/ValidationError"),
                                    @ExampleObject(name = "BadRequest", ref = "#/components/examples/BadRequest")
                            })),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "NotFound", ref = "#/components/examples/NotFound"))),
            @ApiResponse(responseCode = "409", description = "Transición no permitida",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "ConflictTransition", ref = "#/components/examples/ConflictTransition"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InternalError", ref = "#/components/examples/InternalError")))
    })
    public UserResponse changeStatus(
            @Parameter(description = "Identificador interno del usuario", example = USER_ID_EXAMPLE,
                    required = true)
            @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = ChangeUserStatusRequest.class),
                            examples = {
                                    @ExampleObject(name = "Suspender", summary = "ACTIVO → SUSPENDIDO",
                                            value = "{\"status\":\"SUSPENDIDO\"}"),
                                    @ExampleObject(name = "Reactivar", summary = "SUSPENDIDO → ACTIVO",
                                            value = "{\"status\":\"ACTIVO\"}"),
                                    @ExampleObject(name = "BajaLogica", summary = "→ BAJA_LOGICA",
                                            value = "{\"status\":\"BAJA_LOGICA\"}")
                            }))
            @Valid @RequestBody ChangeUserStatusRequest request) {
        return service.changeStatus(id, request);
    }

    /**
     * Actualiza parcialmente los campos permitidos y audita la modificación.
     */
    @PatchMapping("/{id}")
    @Operation(
            summary = "Actualizar datos permitidos",
            description = """
                    Acepta solo nombreTratamiento, email, telefonoContacto y preferencias. \
                    Campos omitidos se conservan; null explícito o campos ajenos (p. ej. datos legales) \
                    se rechazan. Audita ACTUALIZACION OK.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario actualizado",
                    content = @Content(schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(value = USER_RESPONSE_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "Datos o campos no permitidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Validacion", ref = "#/components/examples/ValidationError"),
                                    @ExampleObject(name = "BadRequest", ref = "#/components/examples/BadRequest")
                            })),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "NotFound", ref = "#/components/examples/NotFound"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InternalError", ref = "#/components/examples/InternalError")))
    })
    public UserResponse update(
            @Parameter(description = "Identificador interno del usuario", example = USER_ID_EXAMPLE,
                    required = true)
            @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = UpdateUserRequest.class),
                            examples = @ExampleObject(name = "ContactoYPreferencias", value = """
                                    {
                                      "nombreTratamiento": "Ana Pérez",
                                      "email": "ana.perez@example.com",
                                      "telefonoContacto": "+5491112345678",
                                      "preferencias": {"idioma": "es"}
                                    }
                                    """)))
            @Valid @RequestBody UpdateUserRequest request) {
        return service.update(id, request);
    }

    /**
     * Consulta un usuario por UUID interno con historial de auditoría resumido (hasta 20 eventos).
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Consultar usuario por id",
            description = """
                    Devuelve los datos autorizados del piloto, el estado, la aptitud de canal y hasta \
                    20 eventos recientes de auditoría sin datos de canal ni PII en el detalle.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario encontrado",
                    content = @Content(schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(value = USER_RESPONSE_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "UUID inválido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "BadRequest", ref = "#/components/examples/BadRequest"))),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "NotFound", ref = "#/components/examples/NotFound"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InternalError", ref = "#/components/examples/InternalError")))
    })
    public UserResponse findById(
            @Parameter(description = "Identificador interno del usuario", example = USER_ID_EXAMPLE,
                    required = true)
            @PathVariable UUID id) {
        return service.findById(id);
    }

    /**
     * Da de alta un usuario activo con unicidad por tipo y número de documento.
     */
    @PostMapping
    @Operation(
            summary = "Dar de alta un usuario",
            description = """
                    Crea un usuario en estado ACTIVO con aptitud de vínculo verdadera. \
                    Obligatorio: tipoDocumento, numeroDocumento, nombreTratamiento. \
                    Contacto opcional. El par documental no puede repetirse.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado",
                    content = @Content(schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(name = "Creado", value = """
                                    {
                                      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                                      "tipoDocumento": "DNI",
                                      "numeroDocumento": "30111222",
                                      "nombreTratamiento": "Ana Pérez",
                                      "email": null,
                                      "telefonoContacto": null,
                                      "preferencias": {},
                                      "status": "ACTIVO",
                                      "aptoParaVinculoCanal": true,
                                      "createdAt": "2026-10-15T12:00:00Z",
                                      "updatedAt": "2026-10-15T12:00:00Z",
                                      "historialResumido": []
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o JSON ilegible",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Validacion", ref = "#/components/examples/ValidationError"),
                                    @ExampleObject(name = "BadRequest", ref = "#/components/examples/BadRequest")
                            })),
            @ApiResponse(responseCode = "409", description = "Documento ya registrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "ConflictDuplicate", ref = "#/components/examples/ConflictDuplicate"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InternalError", ref = "#/components/examples/InternalError")))
    })
    public ResponseEntity<UserResponse> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = CreateUserRequest.class),
                            examples = {
                                    @ExampleObject(name = "Minimo", summary = "Alta con datos mínimos",
                                            value = """
                                                    {
                                                      "tipoDocumento": "DNI",
                                                      "numeroDocumento": "30111222",
                                                      "nombreTratamiento": "Ana Pérez"
                                                    }
                                                    """),
                                    @ExampleObject(name = "ConContacto", summary = "Alta con contacto",
                                            value = """
                                                    {
                                                      "tipoDocumento": "DNI",
                                                      "numeroDocumento": "30111222",
                                                      "nombreTratamiento": "Ana Pérez",
                                                      "email": "ana.perez@example.com",
                                                      "telefonoContacto": "+5491112345678"
                                                    }
                                                    """)
                            }))
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + response.id())).body(response);
    }
}
