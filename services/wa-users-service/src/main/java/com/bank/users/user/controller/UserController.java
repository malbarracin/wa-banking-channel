package com.bank.users.user.controller;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.service.UserService;

import lombok.RequiredArgsConstructor;

/** API de alta y consulta de clientes bancarios para H1. */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    /** Devuelve los datos autorizados del piloto y hasta 20 eventos recientes sin datos de canal. */
    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    /** Valida el alta y devuelve 201 con el identificador y los datos autorizados. */
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + response.id())).body(response);
    }
}
