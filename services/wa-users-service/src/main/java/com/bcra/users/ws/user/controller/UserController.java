package com.bcra.users.ws.user.controller;

import java.util.UUID;
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

    public UserController(UserService service) {
        this.service = service;
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
}
