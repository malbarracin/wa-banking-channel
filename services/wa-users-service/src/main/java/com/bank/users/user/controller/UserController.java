package com.bank.users.user.controller;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.user.dto.ChangeUserStatusRequest;
import com.bank.users.user.dto.UpdateUserRequest;
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.dto.UserChannelEligibilityResponse;
import com.bank.users.user.service.UserService;

import lombok.RequiredArgsConstructor;

/** API de alta, consulta y actualización de clientes bancarios para H1. */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    /** Devuelve únicamente identidad interna, estado y aptitud de vínculo, sin datos personales ni historial. */
    @GetMapping("/{id}/channel-eligibility")
    public UserChannelEligibilityResponse channelEligibility(@PathVariable UUID id) {
        return service.channelEligibility(id);
    }

    /** Cambia el estado según la matriz H1 y devuelve 200 con el usuario persistido y su aptitud de canal. */
    @PatchMapping("/{id}/status")
    public UserResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeUserStatusRequest request) {
        return service.changeStatus(id, request);
    }

    /** Actualiza los campos permitidos presentes y devuelve 200 con los datos persistidos. */
    @PatchMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(id, request);
    }

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
