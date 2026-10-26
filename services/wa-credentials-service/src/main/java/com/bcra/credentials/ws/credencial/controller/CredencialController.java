package com.bcra.credentials.ws.credencial.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.credentials.ws.credencial.dto.EmitirCredencialRequest;
import com.bcra.credentials.ws.credencial.dto.EmitirCredencialResponse;
import com.bcra.credentials.ws.credencial.dto.ValidarCredencialRequest;
import com.bcra.credentials.ws.credencial.dto.ValidarCredencialResponse;
import com.bcra.credentials.ws.credencial.service.CredencialService;
import com.bcra.credentials.ws.credencial.service.ValidacionService;

import lombok.RequiredArgsConstructor;

/** API interna de credenciales para el canal. */
@RestController
@RequestMapping("/api/v1/credenciales")
@RequiredArgsConstructor
public class CredencialController {

    private final ValidacionService validacionService;
    private final CredencialService credencialService;

    /** @return identidad vigente, o 401 si la credencial no autoriza operar. */
    @PostMapping("/validar")
    public ValidarCredencialResponse validar(@RequestBody ValidarCredencialRequest request) {
        return validacionService.validar(request.credencial());
    }

    /** @return nueva credencial para H3 y expiración UTC; el canal no debe mostrar el JWT al cliente. */
    @PostMapping("/emitir")
    public ResponseEntity<EmitirCredencialResponse> emitir(@Valid @RequestBody EmitirCredencialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(credencialService.emitir(request));
    }
}
