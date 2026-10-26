package com.bcra.users.ws.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.users.ws.user.dto.UserResponse;
import com.bcra.users.ws.user.dto.UserStatusResponse;
import com.bcra.users.ws.user.mapper.UserMapper;
import com.bcra.users.ws.user.service.UserService;

import lombok.RequiredArgsConstructor;

/** Consultas HTTP de usuarios y su estado operativo por documento. */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;
    private final UserMapper mapper;

    /**
     * Devuelve los datos completos definidos para este hito o 404 si el usuario no existe.
     * El filtrado por perfil se parametrizará cuando Compliance defina la política.
     * @return datos y estado actual del usuario
     */
    @GetMapping("/{tipo}/{numero}")
    public UserResponse consultar(@PathVariable String tipo, @PathVariable String numero) {
        return mapper.toResponse(service.consultar(tipo, numero));
    }

    /**
     * Expone el estado incluso en suspensión o baja lógica, o 404 si no existe.
     * La restricción de nuevos vínculos corresponde al servicio de canal H3.
     * @return documento y estado actual
     */
    @GetMapping("/{tipo}/{numero}/status")
    public UserStatusResponse consultarEstado(@PathVariable String tipo, @PathVariable String numero) {
        return mapper.toStatusResponse(service.consultar(tipo, numero));
    }
}
