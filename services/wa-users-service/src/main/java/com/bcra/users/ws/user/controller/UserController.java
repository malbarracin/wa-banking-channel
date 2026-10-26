package com.bcra.users.ws.user.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.users.ws.user.dto.CreateUserRequest;
import com.bcra.users.ws.user.dto.UserResponse;
import com.bcra.users.ws.user.dto.UserStatusResponse;
import com.bcra.users.ws.user.mapper.UserMapper;
import com.bcra.users.ws.user.service.UserService;

import lombok.RequiredArgsConstructor;

/** API de altas y consultas de usuarios bancarios por documento. */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper mapper;

    /**
     * @param solicitud datos mínimos de alta
     * @return 201 con el usuario activo; 400 por datos incompletos o 409 por documento duplicado
     */
    @PostMapping
    public ResponseEntity<UserResponse> alta(@Valid @RequestBody CreateUserRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.alta(solicitud));
    }

    /**
     * Devuelve los datos completos definidos para este hito o 404 si el usuario no existe.
     * El filtrado por perfil se parametrizará cuando Compliance defina la política.
     * @return datos y estado actual del usuario
     */
    @GetMapping("/{tipo}/{numero}")
    public UserResponse consultar(@PathVariable String tipo, @PathVariable String numero) {
        return mapper.toResponse(userService.consultar(tipo, numero));
    }

    /**
     * Expone el estado incluso en suspensión o baja lógica, o 404 si no existe.
     * La restricción de nuevos vínculos corresponde al servicio de canal H3.
     * @return documento y estado actual
     */
    @GetMapping("/{tipo}/{numero}/status")
    public UserStatusResponse consultarEstado(@PathVariable String tipo, @PathVariable String numero) {
        return mapper.toStatusResponse(userService.consultar(tipo, numero));
    }
}
