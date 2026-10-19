package com.bank.users.user.controller;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bank.users.exception.ErrorResponse;
import com.bank.users.user.dto.ChangeStatusRequest;
import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.user.dto.UpdateUserRequest;
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.service.UserService;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

/** API de alta, consulta, actualización de datos permitidos y cambio de estado de usuarios bancarios. */
@Tag(name = "Usuarios", description = "Alta, consulta, actualización de datos permitidos y cambio de estado operativo de usuarios")
@RestController
@RequestMapping("/api/v1/users")
@Validated
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** Aplica una transición operativa válida y devuelve 200 con el estado y aptitud actuales. */
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Cambiar estado operativo",
            description = "Aplica una transición de estado permitida: ACTIVE→SUSPENDED, ACTIVE→LOGICALLY_DELETED "
                    + "(terminal), SUSPENDED→ACTIVE, SUSPENDED→LOGICALLY_DELETED. "
                    + "LOGICALLY_DELETED es estado terminal y no admite transiciones.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ChangeStatusRequest.class),
                            examples = {
                                    @ExampleObject(name = "Suspender", summary = "ACTIVE → SUSPENDED",
                                            value = "{\"status\":\"SUSPENDED\"}"),
                                    @ExampleObject(name = "Reactivar", summary = "SUSPENDED → ACTIVE",
                                            value = "{\"status\":\"ACTIVE\"}"),
                                    @ExampleObject(name = "Baja lógica",
                                            summary = "ACTIVE|SUSPENDED → LOGICALLY_DELETED",
                                            value = "{\"status\":\"LOGICALLY_DELETED\"}")
                            })))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado aplicado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(name = "Suspendido",
                                    value = "{\"id\":\"550e8400-e29b-41d4-a716-446655440000\","
                                            + "\"documentType\":\"DNI\",\"documentNumber\":\"30111222\","
                                            + "\"displayName\":\"Ana Pérez\",\"email\":\"ana@example.com\","
                                            + "\"phone\":\"+5491112345678\",\"birthDate\":\"1990-01-02\","
                                            + "\"status\":\"SUSPENDED\",\"preferences\":{},"
                                            + "\"channelLinkEligible\":false}"))),
            @ApiResponse(responseCode = "400", description = "Estado inválido o ausente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "ValidationError", value = "{\"code\":\"VALIDATION_ERROR\",\"message\":\"La solicitud tiene datos inválidos\",\"details\":[{\"field\":\"documentType\",\"message\":\"no debe estar vacío\"}],\"timestamp\":\"2026-10-05T14:30:00Z\"}"),
                                    @ExampleObject(name = "BadRequest", value = "{\"code\":\"BAD_REQUEST\",\"message\":\"Solicitud inválida\",\"details\":[],\"timestamp\":\"2026-10-05T14:30:00Z\"}")
                            })),
            @ApiResponse(responseCode = "404", description = "Usuario inexistente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "NotFound", value = "{\"code\":\"NOT_FOUND\",\"message\":\"No existe el usuario solicitado\",\"details\":[],\"timestamp\":\"2026-10-05T14:30:00Z\"}"))),
            @ApiResponse(responseCode = "409", description = "Transición de estado no permitida",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InvalidTransition", value = "{\"code\":\"CONFLICT\",\"message\":\"La transición de estado solicitada no está permitida\",\"details\":[],\"timestamp\":\"2026-10-05T14:30:00Z\"}"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InternalError", value = "{\"code\":\"INTERNAL_ERROR\",\"message\":\"Error interno\",\"details\":[],\"timestamp\":\"2026-10-05T14:30:00Z\"}")))
    })
    @PatchMapping("/{id}/status")
    public UserResponse changeStatus(
            @Parameter(description = "Identificador interno del usuario",
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    in = ParameterIn.PATH)
            @PathVariable UUID id,
            @Valid @RequestBody ChangeStatusRequest request) {
        return userService.changeStatus(id, request);
    }

    /** Actualiza los campos permitidos presentes y devuelve los datos persistidos con estado 200. */
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Actualizar datos permitidos",
            description = "Actualización parcial (PATCH) de displayName, email, phone y preferences. "
                    + "Una clave ausente se omite; una clave con valor null la elimina. "
                    + "Las preferencias reemplazan el mapa completo. "
                    + "Tras aplicar el cambio, debe quedar al menos un contacto (email o phone). "
                    + "Cualquier otra propiedad devuelve 400 BAD_REQUEST.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UpdateUserRequest.class),
                            examples = {
                                    @ExampleObject(name = "DisplayName y preferences",
                                            value = "{\"displayName\":\"Nuevo nombre\","
                                                    + "\"preferences\":{\"language\":\"es\"}}"),
                                    @ExampleObject(name = "Reemplazar contacto",
                                            value = "{\"email\":null,\"phone\":\"+34911234567\"}")
                            })))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Datos actualizados",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(name = "Actualizado",
                                    value = "{\"id\":\"550e8400-e29b-41d4-a716-446655440000\","
                                            + "\"documentType\":\"DNI\",\"documentNumber\":\"30111222\","
                                            + "\"displayName\":\"Nuevo nombre\",\"email\":\"ana@example.com\","
                                            + "\"phone\":\"+5491112345678\",\"birthDate\":\"1990-01-02\","
                                            + "\"status\":\"ACTIVE\",\"preferences\":{\"language\":\"es\"},"
                                            + "\"channelLinkEligible\":true}"))),
            @ApiResponse(responseCode = "400", description = "Valor inválido o campo no permitido",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "ValidationError", value = "{\"code\":\"VALIDATION_ERROR\",\"message\":\"La solicitud tiene datos inválidos\",\"details\":[{\"field\":\"documentType\",\"message\":\"no debe estar vacío\"}],\"timestamp\":\"2026-10-05T14:30:00Z\"}"),
                                    @ExampleObject(name = "BadRequest", value = "{\"code\":\"BAD_REQUEST\",\"message\":\"Solicitud inválida\",\"details\":[],\"timestamp\":\"2026-10-05T14:30:00Z\"}")
                            })),
            @ApiResponse(responseCode = "404", description = "Usuario inexistente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "NotFound", value = "{\"code\":\"NOT_FOUND\",\"message\":\"No existe el usuario solicitado\",\"details\":[],\"timestamp\":\"2026-10-05T14:30:00Z\"}"))),
            @ApiResponse(responseCode = "409", description = "Baja lógica impide modificaciones",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Conflict", value = "{\"code\":\"CONFLICT\",\"message\":\"No se pueden actualizar datos de un usuario dado de baja lógica\",\"details\":[],\"timestamp\":\"2026-10-05T14:30:00Z\"}"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InternalError", value = "{\"code\":\"INTERNAL_ERROR\",\"message\":\"Error interno\",\"details\":[],\"timestamp\":\"2026-10-05T14:30:00Z\"}")))
    })
    @PatchMapping("/{id}")
    public UserResponse update(
            @Parameter(description = "Identificador interno del usuario",
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    in = ParameterIn.PATH)
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    /** Devuelve los datos autorizados y la aptitud para canal del identificador indicado. */
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Consultar usuario por ID interno",
            description = "Devuelve los datos autorizados del usuario (id, documento, nombre, contacto, "
                    + "fecha de nacimiento, estado, preferencias) y el flag channelLinkEligible. "
                    + "No expone datos legales sensibles ni información de verificación de canal.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(name = "Usuario activo",
                                    value = "{\"id\":\"550e8400-e29b-41d4-a716-446655440000\","
                                            + "\"documentType\":\"DNI\",\"documentNumber\":\"30111222\","
                                            + "\"displayName\":\"Ana Pérez\",\"email\":\"ana@example.com\","
                                            + "\"phone\":\"+5491112345678\",\"birthDate\":\"1990-01-02\","
                                            + "\"status\":\"ACTIVE\",\"preferences\":{},"
                                            + "\"channelLinkEligible\":true}"))),
            @ApiResponse(responseCode = "400", description = "UUID mal formado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "BadRequest", value = "{\"code\":\"BAD_REQUEST\",\"message\":\"Solicitud inválida\",\"details\":[],\"timestamp\":\"2026-10-05T14:30:00Z\"}"))),
            @ApiResponse(responseCode = "404", description = "Usuario inexistente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "NotFound", value = "{\"code\":\"NOT_FOUND\",\"message\":\"No existe el usuario solicitado\",\"details\":[],\"timestamp\":\"2026-10-05T14:30:00Z\"}")))
    })
    @GetMapping("/{id}")
    public UserResponse findById(
            @Parameter(description = "Identificador interno del usuario",
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    in = ParameterIn.PATH)
            @PathVariable UUID id) {
        return userService.findById(id);
    }

    /** Consulta un documento completo; ambos parámetros son obligatorios y no ofrece listado general. */
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Consultar usuario por documento",
            description = "Busca un usuario por tipo y número de documento. Ambos parámetros son "
                    + "obligatorios. La búsqueda usa la clave compuesta (tipo + número).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(name = "Usuario encontrado",
                                    value = "{\"id\":\"550e8400-e29b-41d4-a716-446655440000\","
                                            + "\"documentType\":\"DNI\",\"documentNumber\":\"30111222\","
                                            + "\"displayName\":\"Ana Pérez\",\"email\":\"ana@example.com\","
                                            + "\"phone\":\"+5491112345678\",\"birthDate\":\"1990-01-02\","
                                            + "\"status\":\"ACTIVE\",\"preferences\":{},"
                                            + "\"channelLinkEligible\":true}"))),
            @ApiResponse(responseCode = "400", description = "Parámetros ausentes, tipo inválido o formato incorrecto",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "BadRequest", value = "{\"code\":\"BAD_REQUEST\",\"message\":\"Solicitud inválida\",\"details\":[],\"timestamp\":\"2026-10-05T14:30:00Z\"}"),
                                    @ExampleObject(name = "ValidationError", value = "{\"code\":\"VALIDATION_ERROR\",\"message\":\"La solicitud tiene datos inválidos\",\"details\":[{\"field\":\"documentType\",\"message\":\"no debe estar vacío\"}],\"timestamp\":\"2026-10-05T14:30:00Z\"}")
                            })),
            @ApiResponse(responseCode = "404", description = "Usuario inexistente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "NotFound", value = "{\"code\":\"NOT_FOUND\",\"message\":\"No existe el usuario solicitado\",\"details\":[],\"timestamp\":\"2026-10-05T14:30:00Z\"}")))
    })
    @GetMapping
    public UserResponse findByDocument(
            @Parameter(description = "Tipo de documento", example = "DNI",
                    in = ParameterIn.QUERY, required = true)
            @RequestParam DocumentType documentType,
            @Parameter(description = "Número de documento, alfanumérico sin espacios", example = "30111222",
                    in = ParameterIn.QUERY, required = true)
            @RequestParam @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9]+") String documentNumber) {
        return userService.findByDocument(documentType, documentNumber);
    }

    /** Valida los datos mínimos y devuelve 201 con el usuario activo y su identificador. */
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Dar de alta un usuario",
            description = "Crea un usuario en estado ACTIVO con los datos mínimos (tipo y número de "
                    + "documento, nombre para tratamiento, contacto y fecha de nacimiento). "
                    + "El tipo y número de documento no pueden repetirse. "
                    + "Al menos uno de email o phone es obligatorio.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CreateUserRequest.class),
                            examples = {
                                    @ExampleObject(name = "Con email",
                                            value = "{\"documentType\":\"DNI\",\"documentNumber\":\"30111222\","
                                                    + "\"displayName\":\"Ana Pérez\",\"email\":\"ana@example.com\","
                                                    + "\"birthDate\":\"1990-01-02\"}"),
                                    @ExampleObject(name = "Con teléfono",
                                            value = "{\"documentType\":\"DNI\",\"documentNumber\":\"30111222\","
                                                    + "\"displayName\":\"Ana Pérez\",\"phone\":\"+5491112345678\","
                                                    + "\"birthDate\":\"1990-01-02\"}"),
                                    @ExampleObject(name = "Con ambos contactos",
                                            value = "{\"documentType\":\"DNI\",\"documentNumber\":\"30111222\","
                                                    + "\"displayName\":\"Ana Pérez\",\"email\":\"ana@example.com\","
                                                    + "\"phone\":\"+549112345678\",\"birthDate\":\"1990-01-02\"}")
                            })))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(name = "Usuario activo creado",
                                    value = "{\"id\":\"550e8400-e29b-41d4-a716-446655440000\","
                                            + "\"documentType\":\"DNI\",\"documentNumber\":\"30111222\","
                                            + "\"displayName\":\"Ana Pérez\",\"email\":\"ana@example.com\","
                                            + "\"phone\":\"+5491112345678\",\"birthDate\":\"1990-01-02\","
                                            + "\"status\":\"ACTIVE\",\"preferences\":{},"
                                            + "\"channelLinkEligible\":true}"))),
            @ApiResponse(responseCode = "400", description = "Datos faltantes o formato inválido",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "ValidationError", value = "{\"code\":\"VALIDATION_ERROR\",\"message\":\"La solicitud tiene datos inválidos\",\"details\":[{\"field\":\"documentType\",\"message\":\"no debe estar vacío\"}],\"timestamp\":\"2026-10-05T14:30:00Z\"}"),
                                    @ExampleObject(name = "BadRequest", value = "{\"code\":\"BAD_REQUEST\",\"message\":\"Solicitud inválida\",\"details\":[],\"timestamp\":\"2026-10-05T14:30:00Z\"}")
                            })),
            @ApiResponse(responseCode = "409", description = "Documento ya registrado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Duplicate", value = "{\"code\":\"CONFLICT\",\"message\":\"Ya existe un usuario con ese documento\",\"details\":[],\"timestamp\":\"2026-10-05T14:30:00Z\"}"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InternalError", value = "{\"code\":\"INTERNAL_ERROR\",\"message\":\"Error interno\",\"details\":[],\"timestamp\":\"2026-10-05T14:30:00Z\"}")))
    })
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + response.id())).body(response);
    }
}
