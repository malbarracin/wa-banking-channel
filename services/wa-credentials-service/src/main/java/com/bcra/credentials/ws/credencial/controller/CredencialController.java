package com.bcra.credentials.ws.credencial.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.credentials.ws.credencial.dto.EmitirCredencialRequest;
import com.bcra.credentials.ws.credencial.dto.EmitirCredencialResponse;
import com.bcra.credentials.ws.credencial.dto.RevocarCredencialRequest;
import com.bcra.credentials.ws.credencial.service.CredencialService;

import lombok.RequiredArgsConstructor;

/** API interna de credenciales para el canal. */
@RestController
@RequestMapping("/api/v1/credenciales")
@Validated
@RequiredArgsConstructor
public class CredencialController {

    private final CredencialService credencialService;

    /** Revoca antes de responder; devuelve 404 si no existe credencial activa y 204 al confirmar. */
    @PostMapping("/{numero}/revocacion")
    public ResponseEntity<Void> revocar(
            @PathVariable @Pattern(regexp = "\\+[1-9][0-9]{1,14}") String numero,
            @Valid @RequestBody RevocarCredencialRequest request) {
        credencialService.revocar(numero, request);
        return ResponseEntity.noContent().build();
    }

    /** @return nueva credencial para H3 y expiración UTC; el canal no debe mostrar el JWT al cliente. */
    @PostMapping("/emitir")
    public ResponseEntity<EmitirCredencialResponse> emitir(@Valid @RequestBody EmitirCredencialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(credencialService.emitir(request));
    }
}
