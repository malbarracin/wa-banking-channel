package com.bcra.users.ws.user.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.users.ws.user.dto.CreateUserRequest;
import com.bcra.users.ws.user.dto.UserResponse;
import com.bcra.users.ws.user.service.UserService;

import lombok.RequiredArgsConstructor;

/** API de altas de usuarios bancarios. */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * @param solicitud datos mínimos de alta
     * @return 201 con el usuario activo; 400 por datos incompletos o 409 por documento duplicado
     */
    @PostMapping
    public ResponseEntity<UserResponse> alta(@Valid @RequestBody CreateUserRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.alta(solicitud));
    }
}
