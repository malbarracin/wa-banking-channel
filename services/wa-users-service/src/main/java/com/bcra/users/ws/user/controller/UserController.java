package com.bcra.users.ws.user.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.bcra.users.ws.exception.ErrorResponse;
import com.bcra.users.ws.exception.ForbiddenException;
import com.bcra.users.ws.user.dto.CreateUserRequest;
import com.bcra.users.ws.user.dto.UpdateStatusRequest;
import com.bcra.users.ws.user.dto.UpdateUserRequest;
import com.bcra.users.ws.user.dto.UserResponse;
import com.bcra.users.ws.user.dto.UserStatusResponse;
import com.bcra.users.ws.user.mapper.UserMapper;
import com.bcra.users.ws.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** API de altas, consultas y actualizaciones de usuarios bancarios por documento. */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Alta, consulta, actualización y estado de usuarios bancarios (H1)")
public class UserController {

    private static final String USER_RESPONSE_EXAMPLE = """
            {
              "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
              "documento": { "tipo": "DNI", "numero": "30111222" },
              "nombre": "Ana",
              "apellido": "Pérez",
              "fechaNacimiento": "1990-05-15",
              "email": "ana@example.test",
              "telefono": "5551234",
              "nombreTratamiento": null,
              "preferencias": null,
              "estado": "ACTIVE"
            }
            """;

    private static final String STATUS_RESPONSE_EXAMPLE = """
            {
              "documento": { "tipo": "DNI", "numero": "30111222" },
              "estado": "SUSPENDED"
            }
            """;

    private final UserService userService;
    private final UserMapper mapper;

    /**
     * Cambia el estado según las transiciones soportadas: 400 si es inválida y 404 si no existe el usuario.
     * @return documento y estado confirmado en persistencia
     */
    @PatchMapping("/{tipo}/{numero}/status")
    @Operation(
            summary = "Cambiar estado del usuario",
            description = """
                    Transiciones soportadas en H1: ACTIVE→SUSPENDED|LOGICAL_DELETE, \
                    SUSPENDED→ACTIVE|LOGICAL_DELETE. Desde LOGICAL_DELETE no hay reactivación. \
                    Transición inválida → 400 BAD_REQUEST (no 409).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado",
                    content = @Content(schema = @Schema(implementation = UserStatusResponse.class),
                            examples = @ExampleObject(value = STATUS_RESPONSE_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "Transición no soportada o body inválido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "InvalidTransition",
                                            ref = "#/components/examples/InvalidTransition"),
                                    @ExampleObject(name = "BadRequest",
                                            ref = "#/components/examples/BadRequest")
                            })),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/NotFound"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/InternalError")))
    })
    public UserStatusResponse cambiarEstado(
            @Parameter(description = "Tipo de documento", example = "DNI") @PathVariable String tipo,
            @Parameter(description = "Número de documento", example = "30111222") @PathVariable String numero,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = UpdateStatusRequest.class),
                            examples = {
                                    @ExampleObject(name = "Suspender", value = "{\"estado\":\"SUSPENDED\"}"),
                                    @ExampleObject(name = "Reactivar", value = "{\"estado\":\"ACTIVE\"}"),
                                    @ExampleObject(name = "Baja lógica",
                                            value = "{\"estado\":\"LOGICAL_DELETE\"}")
                            }))
            @RequestBody UpdateStatusRequest request) {
        return mapper.toStatusResponse(userService.cambiarEstado(tipo, numero, request));
    }

    /**
     * @param solicitud datos mínimos de alta
     * @return 201 con el usuario activo; 400 por datos incompletos o 409 por documento duplicado
     */
    @PostMapping
    @Operation(
            summary = "Dar de alta un usuario",
            description = """
                    Crea un usuario ACTIVE con documento (tipo+número) como clave única. \
                    Obligatorios: documento, nombre, apellido, fecha de nacimiento y al menos \
                    email o teléfono. No exige formato cerrado de contacto ni catálogo de documentos.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado",
                    content = @Content(schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(value = USER_RESPONSE_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o incompletos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "ValidationError",
                                            ref = "#/components/examples/ValidationError"),
                                    @ExampleObject(name = "BadRequest",
                                            ref = "#/components/examples/BadRequest")
                            })),
            @ApiResponse(responseCode = "409", description = "Documento ya registrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/Duplicate"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/InternalError")))
    })
    public ResponseEntity<UserResponse> alta(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = CreateUserRequest.class),
                            examples = @ExampleObject(name = "DNI", value = """
                                    {
                                      "documento": { "tipo": "DNI", "numero": "30111222" },
                                      "nombre": "Ana",
                                      "apellido": "Pérez",
                                      "fechaNacimiento": "1990-05-15",
                                      "email": "ana@example.test",
                                      "telefono": "5551234"
                                    }
                                    """)))
            @Valid @RequestBody CreateUserRequest solicitud) {
        UserResponse usuario = userService.alta(solicitud);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{tipo}/{numero}")
                .buildAndExpand(usuario.documento().tipo(), usuario.documento().numero()).encode().toUri())
                .body(usuario);
    }

    /**
     * Actualiza datos permitidos o devuelve 404. UpdateUserRequest rechaza propiedades desconocidas
     * con JsonAnySetter; UserUpdateExceptionHandler responde 400 y audita el rechazo sin aplicar cambios.
     * @return datos guardados del usuario
     */
    @PatchMapping("/{tipo}/{numero}")
    @Operation(
            summary = "Actualizar datos permitidos",
            description = """
                    Solo email, telefono, nombreTratamiento y preferencias. Campos ausentes o null \
                    conservan el valor. Propiedades fuera del contrato (documento, fechaNacimiento, \
                    estado, etc.) → 400 BAD_REQUEST sin aplicar cambios.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario actualizado",
                    content = @Content(schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                                      "documento": { "tipo": "DNI", "numero": "30111222" },
                                      "nombre": "Ana",
                                      "apellido": "Pérez",
                                      "fechaNacimiento": "1990-05-15",
                                      "email": "nuevo@example.test",
                                      "telefono": "555999",
                                      "nombreTratamiento": "Anita",
                                      "preferencias": "canal:whatsapp",
                                      "estado": "ACTIVE"
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "Campo no editable o body inválido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "NonEditableField",
                                            ref = "#/components/examples/NonEditableField"),
                                    @ExampleObject(name = "BadRequest",
                                            ref = "#/components/examples/BadRequest")
                            })),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/NotFound"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/InternalError")))
    })
    public UserResponse actualizar(
            @Parameter(description = "Tipo de documento", example = "DNI") @PathVariable String tipo,
            @Parameter(description = "Número de documento", example = "30111222") @PathVariable String numero,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = UpdateUserRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "email": "nuevo@example.test",
                                      "telefono": "555999",
                                      "nombreTratamiento": "Anita",
                                      "preferencias": "canal:whatsapp"
                                    }
                                    """)))
            @RequestBody UpdateUserRequest request) {
        return mapper.toResponse(userService.actualizar(tipo, numero, request));
    }

    /**
     * Devuelve los datos completos definidos para este hito o 404 si el usuario no existe.
     * Acceso provisional reservado al rol autenticado USERS_FULL_READ hasta la política de Compliance.
     * @return datos y estado actual del usuario
     */
    @GetMapping("/{tipo}/{numero}")
    @Operation(
            summary = "Consultar usuario por documento",
            description = """
                    Devuelve el UserResponse completo de este hito (provisional hasta que Compliance \
                    defina el filtrado por perfil). Requiere principal autenticado y rol de servidor
                    USERS_FULL_READ; sin ellos devuelve 403. Incluye usuarios en cualquier estado.""")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "Sin principal o rol USERS_FULL_READ",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "200", description = "Usuario encontrado",
                    content = @Content(schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(value = USER_RESPONSE_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado (sin PII en el error)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/NotFound"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/InternalError")))
    })
    public UserResponse consultar(
            HttpServletRequest request,
            @Parameter(description = "Tipo de documento", example = "DNI") @PathVariable String tipo,
            @Parameter(description = "Número de documento", example = "30111222") @PathVariable String numero) {
        if (request.getUserPrincipal() == null || !request.isUserInRole("USERS_FULL_READ")) {
            throw new ForbiddenException();
        }
        return mapper.toResponse(userService.consultar(tipo, numero));
    }

    /**
     * Expone el estado incluso en suspensión o baja lógica, o 404 si no existe.
     * La restricción de nuevos vínculos corresponde al servicio de canal H3.
     * @return documento y estado actual
     */
    @GetMapping("/{tipo}/{numero}/status")
    @Operation(
            summary = "Consultar estado del usuario",
            description = """
                    Expone documento y estado (ACTIVE, SUSPENDED, LOGICAL_DELETE) para que H3 \
                    evalúe habilitación de nuevos vínculos de canal. No aplica la restricción aquí.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado del usuario",
                    content = @Content(schema = @Schema(implementation = UserStatusResponse.class),
                            examples = @ExampleObject(value = STATUS_RESPONSE_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/NotFound"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/InternalError")))
    })
    public UserStatusResponse consultarEstado(
            @Parameter(description = "Tipo de documento", example = "DNI") @PathVariable String tipo,
            @Parameter(description = "Número de documento", example = "30111222") @PathVariable String numero) {
        return mapper.toStatusResponse(userService.consultar(tipo, numero));
    }
}
