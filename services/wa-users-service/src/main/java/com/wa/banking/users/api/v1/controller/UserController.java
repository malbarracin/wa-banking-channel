package com.wa.banking.users.api.v1.controller;

import com.wa.banking.users.api.error.ErrorResponse;
import com.wa.banking.users.api.v1.dto.ChangeUserStatusRequestV1;
import com.wa.banking.users.api.v1.dto.CreateUserRequestV1;
import com.wa.banking.users.api.v1.dto.UpdateUserRequestV1;
import com.wa.banking.users.api.v1.dto.UserAuditResponseV1;
import com.wa.banking.users.api.v1.dto.UserResponseV1;
import com.wa.banking.users.config.OpenApiExamples;
import com.wa.banking.users.entity.DocumentType;
import com.wa.banking.users.service.UserService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST v1 para operaciones de usuarios bancarios (flujos U1–U4).
 *
 * @author licius-it
 * @since 2026-06-22
 * @contact marceloalejandro.albarracin@gmail.com
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users V1", description = "Operaciones de usuarios bancarios - API v1 (flujos U1–U4)")
public class UserController {

    private final UserService userService;

    @PostMapping
    @Operation(
            summary = "U1 - Alta de usuario bancario",
            description = "Registra un nuevo usuario con estado ACTIVE. El documento (tipo + número) debe ser único."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Usuario creado exitosamente",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponseV1.class),
                            examples = @ExampleObject(name = "Usuario activo", value = OpenApiExamples.USER_CREATED_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validación fallida o documento duplicado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Documento duplicado",
                                            value = OpenApiExamples.DUPLICATE_DOCUMENT_ERROR
                                    ),
                                    @ExampleObject(
                                            name = "Error de validación",
                                            value = OpenApiExamples.VALIDATION_ERROR
                                    )
                            }
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
    public ResponseEntity<UserResponseV1> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Datos del nuevo usuario bancario",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CreateUserRequestV1.class),
                            examples = @ExampleObject(name = "Alta usuario activo", value = OpenApiExamples.CREATE_USER_REQUEST)
                    )
            )
            @Valid @RequestBody CreateUserRequestV1 request) {
        UserResponseV1 response = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "U2 - Consulta de usuario por identificador interno",
            description = "Obtiene un usuario por su ID interno generado por MongoDB."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Usuario encontrado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponseV1.class),
                            examples = @ExampleObject(name = "Usuario activo", value = OpenApiExamples.USER_CREATED_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado",
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
    public ResponseEntity<UserResponseV1> findById(
            @Parameter(description = "Identificador interno del usuario", example = "665f1a2b3c4d5e6f7a8b9c0d")
            @PathVariable String id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @GetMapping("/by-document")
    @Operation(
            summary = "U2 - Consulta de usuario por tipo y número de documento",
            description = "Busca un usuario por la combinación única de tipo y número de documento."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Usuario encontrado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponseV1.class),
                            examples = @ExampleObject(name = "Usuario activo", value = OpenApiExamples.USER_CREATED_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado",
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
    public ResponseEntity<UserResponseV1> findByDocument(
            @Parameter(description = "Tipo de documento", example = "DNI")
            @RequestParam DocumentType documentType,
            @Parameter(description = "Número de documento", example = "12345678")
            @RequestParam String documentNumber) {
        return ResponseEntity.ok(userService.findByDocument(documentType, documentNumber));
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "U3 - Actualización de campos permitidos",
            description = "Actualiza parcialmente displayName, email, phone y preferences. "
                    + "Los campos documentType, documentNumber y status no son modificables."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Usuario actualizado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponseV1.class),
                            examples = @ExampleObject(name = "Usuario actualizado", value = OpenApiExamples.USER_UPDATED_RESPONSE)
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
                    description = "Usuario no encontrado",
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
    public ResponseEntity<UserResponseV1> update(
            @Parameter(description = "Identificador interno del usuario", example = "665f1a2b3c4d5e6f7a8b9c0d")
            @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Campos mutables a actualizar (parcial)",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UpdateUserRequestV1.class),
                            examples = @ExampleObject(name = "Update campos permitidos", value = OpenApiExamples.UPDATE_USER_REQUEST)
                    )
            )
            @Valid @RequestBody UpdateUserRequestV1 request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(
            summary = "U4 - Cambio de estado operativo",
            description = "Cambia el estado del usuario. Transiciones permitidas: "
                    + "ACTIVE→SUSPENDED|SOFT_DELETED, SUSPENDED→ACTIVE|SOFT_DELETED. "
                    + "SOFT_DELETED es terminal (sin reactivación)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Estado actualizado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponseV1.class),
                            examples = @ExampleObject(name = "Usuario suspendido", value = OpenApiExamples.USER_SUSPENDED_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Transición de estado inválida",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Transición inválida",
                                    value = OpenApiExamples.INVALID_TRANSITION_ERROR
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado",
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
    public ResponseEntity<UserResponseV1> changeStatus(
            @Parameter(description = "Identificador interno del usuario", example = "665f1a2b3c4d5e6f7a8b9c0d")
            @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Nuevo estado operativo",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ChangeUserStatusRequestV1.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Suspender usuario",
                                            value = OpenApiExamples.CHANGE_STATUS_SUSPENDED_REQUEST
                                    ),
                                    @ExampleObject(
                                            name = "Baja lógica",
                                            value = OpenApiExamples.CHANGE_STATUS_SOFT_DELETED_REQUEST
                                    )
                            }
                    )
            )
            @Valid @RequestBody ChangeUserStatusRequestV1 request) {
        return ResponseEntity.ok(userService.changeStatus(id, request));
    }

    @GetMapping("/{id}/audit")
    @Operation(
            summary = "Historial de auditoría paginado",
            description = "Consulta el historial de auditoría de un usuario para soporte operativo."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Historial de auditoría",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(name = "Página de auditoría", value = OpenApiExamples.AUDIT_PAGE_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado",
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
    public ResponseEntity<Page<UserAuditResponseV1>> getAuditHistory(
            @Parameter(description = "Identificador interno del usuario", example = "665f1a2b3c4d5e6f7a8b9c0d")
            @PathVariable String id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userService.getAuditHistory(id, pageable));
    }
}
