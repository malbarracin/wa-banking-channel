package com.bcra.users.ws.user.controller;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.bcra.users.ws.config.BankUsersProperties;
import com.bcra.users.ws.exception.ErrorResponse;
import com.bcra.users.ws.user.dto.ChangeUserStatusRequest;
import com.bcra.users.ws.user.dto.CreateUserRequest;
import com.bcra.users.ws.user.dto.UpdateUserRequest;
import com.bcra.users.ws.user.dto.UserResponse;
import com.bcra.users.ws.user.exception.InvalidUpdateFieldException;
import com.bcra.users.ws.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API de alta, consulta, actualización y cambio de estado de clientes. */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Clientes", description = "Alta, consulta, actualización y estado operativo de clientes")
public class UserController {

    private static final String USER_ID_EXAMPLE = "123e4567-e89b-12d3-a456-426614174000";
    private static final String USER_RESPONSE_EXAMPLE = """
            {"id":"123e4567-e89b-12d3-a456-426614174000","externalId":"USR-00042",\
            "displayName":"Ana Pérez","contactEmail":"ana.perez@example.com",\
            "contactPhone":"+5491112345678","status":"ACTIVE","createdAt":"2026-11-27T12:00:00Z"}""";

    private final UserService service;
    private final BankUsersProperties policy;
    private final ObjectMapper objectMapper;

    public UserController(UserService service, BankUsersProperties policy, ObjectMapper objectMapper) {
        this.service = service;
        this.policy = policy;
        this.objectMapper = objectMapper;
    }

    /**
     * Crea un cliente activo y registra el actor; por defecto se utiliza system.
     *
     * @param request datos del alta
     * @param actor   operador que solicita el alta
     * @return cliente creado en estado ACTIVE
     */
    @Operation(
            summary = "Dar de alta un cliente",
            description = "Crea un cliente ACTIVE con los datos mínimos de bank.users.required-fields "
                    + "y audita el alta. Rechaza duplicados del campo de unicidad.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = CreateUserRequest.class),
                            examples = @ExampleObject(
                                    name = "Alta mínima",
                                    value = "{\"uniquenessValue\":\"CLI-1001\",\"displayName\":\"Ana Pérez\","
                                            + "\"contactEmail\":\"ana.perez@example.com\","
                                            + "\"contactPhone\":\"+5491112345678\"}"))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cliente creado",
                    content = @Content(schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(value = USER_RESPONSE_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o incompletos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Validación",
                                            ref = "#/components/examples/ValidationError"),
                                    @ExampleObject(name = "Solicitud inválida",
                                            ref = "#/components/examples/BadRequest")})),
            @ApiResponse(responseCode = "409", description = "Valor de unicidad ya registrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Duplicate",
                                    ref = "#/components/examples/Duplicate"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InternalError",
                                    ref = "#/components/examples/InternalError")))})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(
            @RequestBody CreateUserRequest request,
            @Parameter(description = "Actor del alta; system si se omite", example = "operador-123")
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        return service.create(request, actor);
    }

    /**
     * Consulta los datos autorizados y el estado del cliente.
     *
     * @param id identificador interno
     * @return campos públicos y estado
     */
    @Operation(summary = "Consultar cliente",
            description = "Devuelve solo los campos de bank.users.public-fields y el estado actual.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cliente encontrado",
                    content = @Content(schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(value = USER_RESPONSE_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "Identificador inválido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "BadRequest",
                                    ref = "#/components/examples/BadRequest"))),
            @ApiResponse(responseCode = "404", description = "Cliente inexistente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "NotFound",
                                    ref = "#/components/examples/NotFound"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InternalError",
                                    ref = "#/components/examples/InternalError")))})
    @GetMapping("/{id}")
    public UserResponse find(
            @Parameter(description = "Identificador interno", example = USER_ID_EXAMPLE)
            @PathVariable UUID id) {
        return service.find(id);
    }

    /**
     * Actualiza los campos configurados y registra el actor de la modificación.
     *
     * @param id    identificador interno
     * @param body  cuerpo con solo campos actualizables
     * @param actor operador que solicita el cambio
     * @return cliente actualizado
     */
    @Operation(
            summary = "Actualizar cliente",
            description = "Actualiza solo campos de bank.users.updatable-fields. "
                    + "Cualquier otro campo se rechaza con 400 BAD_REQUEST.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = UpdateUserRequest.class),
                            examples = {
                                    @ExampleObject(name = "Nombre",
                                            value = "{\"displayName\":\"Juan Pérez\"}"),
                                    @ExampleObject(name = "Contacto",
                                            value = "{\"contactEmail\":\"juan.perez@example.com\","
                                                    + "\"contactPhone\":\"+5491198765432\"}")})))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cliente actualizado",
                    content = @Content(schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(value = """
                                    {"id":"123e4567-e89b-12d3-a456-426614174000","externalId":"USR-00042",\
                                    "displayName":"Juan Pérez","contactEmail":"juan.perez@example.com",\
                                    "contactPhone":"+5491198765432","status":"ACTIVE",\
                                    "createdAt":"2026-11-27T12:00:00Z"}"""))),
            @ApiResponse(responseCode = "400", description = "Campo no permitido o dato inválido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Campo no permitido", value =
                                            "{\"code\":\"BAD_REQUEST\",\"message\":\"Campo no actualizable\","
                                                    + "\"details\":[{\"field\":\"uniquenessValue\","
                                                    + "\"message\":\"Campo no permitido\"}],"
                                                    + "\"timestamp\":\"2026-11-27T12:00:00Z\"}"),
                                    @ExampleObject(name = "Validación",
                                            ref = "#/components/examples/ValidationError")})),
            @ApiResponse(responseCode = "404", description = "Cliente inexistente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "NotFound",
                                    ref = "#/components/examples/NotFound"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InternalError",
                                    ref = "#/components/examples/InternalError")))})
    @PatchMapping("/{id}")
    public UserResponse update(
            @Parameter(description = "Identificador interno", example = USER_ID_EXAMPLE)
            @PathVariable UUID id,
            @RequestBody JsonNode body,
            @Parameter(description = "Actor de la modificación", example = "operador-123")
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        if (!body.isObject() || body.isEmpty()) {
            throw new IllegalArgumentException("El cuerpo debe contener campos actualizables");
        }
        Set<String> fields = new HashSet<>();
        body.fieldNames().forEachRemaining(fields::add);
        for (String field : fields) {
            if (!policy.updatableFields().contains(field) || field.equals(policy.uniquenessField())
                    || !Set.of("displayName", "contactEmail", "contactPhone").contains(field)) {
                throw new InvalidUpdateFieldException(field);
            }
            if (!body.get(field).isTextual()) {
                throw new IllegalArgumentException("Valor inválido");
            }
        }
        return service.update(id, objectMapper.convertValue(body, UpdateUserRequest.class), fields, actor);
    }

    /**
     * Cambia el estado operativo del cliente según la política de transiciones.
     *
     * @param id      identificador interno
     * @param request estado destino y actor
     * @return cliente con el nuevo estado
     */
    @Operation(
            summary = "Cambiar estado operativo",
            description = "Aplica una transición permitida por bank.users.allowed-transitions "
                    + "(ACTIVE/SUSPENDED/INACTIVE) y audita el cambio. Solo ACTIVE es elegible "
                    + "para un vínculo de canal nuevo.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = ChangeUserStatusRequest.class),
                            examples = {
                                    @ExampleObject(name = "Suspender",
                                            value = "{\"status\":\"SUSPENDED\",\"actor\":\"operador-123\"}"),
                                    @ExampleObject(name = "Reactivar",
                                            value = "{\"status\":\"ACTIVE\",\"actor\":\"operador-123\"}"),
                                    @ExampleObject(name = "Baja lógica",
                                            value = "{\"status\":\"INACTIVE\",\"actor\":\"operador-123\"}")})))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado",
                    content = @Content(schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(value = """
                                    {"id":"123e4567-e89b-12d3-a456-426614174000","externalId":"USR-00042",\
                                    "displayName":"Ana Pérez","contactEmail":"ana.perez@example.com",\
                                    "contactPhone":"+5491112345678","status":"SUSPENDED",\
                                    "createdAt":"2026-11-27T12:00:00Z"}"""))),
            @ApiResponse(responseCode = "400", description = "Estado inválido o datos incompletos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "BadRequest",
                                            ref = "#/components/examples/BadRequest"),
                                    @ExampleObject(name = "ValidationError",
                                            ref = "#/components/examples/ValidationError")})),
            @ApiResponse(responseCode = "404", description = "Cliente inexistente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "NotFound",
                                    ref = "#/components/examples/NotFound"))),
            @ApiResponse(responseCode = "409", description = "Transición no permitida",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InvalidTransition",
                                    ref = "#/components/examples/InvalidTransition"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "InternalError",
                                    ref = "#/components/examples/InternalError")))})
    @PatchMapping("/{id}/status")
    public UserResponse changeStatus(
            @Parameter(description = "Identificador interno", example = USER_ID_EXAMPLE)
            @PathVariable UUID id,
            @Valid @RequestBody ChangeUserStatusRequest request) {
        return service.changeStatus(id, request);
    }
}
