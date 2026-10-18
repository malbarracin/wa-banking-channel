package com.bank.users.user.controller;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.service.UserService;

import lombok.RequiredArgsConstructor;

/** API de alta y consulta de usuarios bancarios. */
@RestController
@RequestMapping("/api/v1/users")
@Validated
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** Devuelve los datos autorizados y la aptitud para canal del identificador indicado. */
    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable UUID id) {
        return userService.findById(id);
    }

    /** Consulta un documento completo; ambos parámetros son obligatorios y no ofrece listado general. */
    @GetMapping
    public UserResponse findByDocument(@RequestParam DocumentType documentType,
            @RequestParam @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9]+") String documentNumber) {
        return userService.findByDocument(documentType, documentNumber);
    }

    /** Valida los datos mínimos y devuelve 201 con el usuario activo y su identificador. */
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + response.id())).body(response);
    }
}
