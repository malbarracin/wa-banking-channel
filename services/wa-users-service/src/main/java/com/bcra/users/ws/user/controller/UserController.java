package com.bcra.users.ws.user.controller;

import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import com.bcra.users.ws.config.BankUsersProperties;
import com.bcra.users.ws.user.dto.UpdateUserRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bcra.users.ws.user.dto.CreateUserRequest;
import com.bcra.users.ws.user.dto.UserResponse;
import com.bcra.users.ws.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API de alta y consulta de clientes. */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService service;
    private final BankUsersProperties policy;
    private final ObjectMapper objectMapper;

    public UserController(UserService service, BankUsersProperties policy, ObjectMapper objectMapper) {
        this.service = service;
        this.policy = policy;
        this.objectMapper = objectMapper;
    }

    /** Crea un cliente activo y registra el actor; por defecto se utiliza system. */
    @Operation(summary = "Dar de alta un cliente", description = "Crea un cliente activo y audita el alta.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Cliente creado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "409", description = "Valor duplicado"),
            @ApiResponse(responseCode = "500", description = "Error interno")})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@RequestBody CreateUserRequest request,
            @Parameter(description = "Actor del alta; system si se omite", example = "operator")
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        return service.create(request, actor);
    }

    /** Consulta los datos autorizados y el estado del cliente. */
    @Operation(summary = "Consultar cliente", description = "Devuelve los campos públicos y el estado actual.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Cliente encontrado"),
            @ApiResponse(responseCode = "400", description = "Identificador inválido"),
            @ApiResponse(responseCode = "404", description = "Cliente inexistente"),
            @ApiResponse(responseCode = "500", description = "Error interno")})
    @GetMapping("/{id}")
    public UserResponse find(@Parameter(description = "Identificador interno", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID id) {
        return service.find(id);
    }

    /** Actualiza los campos configurados y registra el actor de la modificación. */
    @Operation(summary = "Actualizar cliente", description = "Actualiza los campos autorizados por el banco.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Cliente actualizado"),
            @ApiResponse(responseCode = "400", description = "Campo no permitido o dato inválido"),
            @ApiResponse(responseCode = "404", description = "Cliente inexistente"),
            @ApiResponse(responseCode = "500", description = "Error interno")})
    @PatchMapping("/{id}")
    public UserResponse update(@Parameter(description = "Identificador interno", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID id, @RequestBody JsonNode body,
            @Parameter(description = "Actor de la modificación", example = "operator")
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        if (!body.isObject() || body.isEmpty()) {
            throw new IllegalArgumentException("El cuerpo debe contener campos actualizables");
        }
        Set<String> fields = new HashSet<>();
        body.fieldNames().forEachRemaining(fields::add);
        for (String field : fields) {
            if (!policy.updatableFields().contains(field) || field.equals(policy.uniquenessField())
                    || !Set.of("displayName", "contactEmail", "contactPhone").contains(field)) {
                throw new com.bcra.users.ws.user.exception.InvalidUpdateFieldException(field);
            }
            if (!body.get(field).isTextual()) {
                throw new IllegalArgumentException("Valor inválido");
            }
        }
        return service.update(id, objectMapper.convertValue(body, UpdateUserRequest.class), fields, actor);
    }
}
