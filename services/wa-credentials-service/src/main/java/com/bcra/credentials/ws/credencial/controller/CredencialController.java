package com.bcra.credentials.ws.credencial.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.credentials.ws.credencial.dto.ValidarCredencialRequest;
import com.bcra.credentials.ws.credencial.dto.ValidarCredencialResponse;
import com.bcra.credentials.ws.credencial.service.ValidacionService;

import lombok.RequiredArgsConstructor;

/** Validación de credenciales para los servicios de productos. */
@RestController
@RequestMapping("/api/v1/credenciales")
@RequiredArgsConstructor
public class CredencialController {

    private final ValidacionService validacionService;

    /** @return identidad vigente, o 401 si la credencial no autoriza operar. */
    @PostMapping("/validar")
    public ValidarCredencialResponse validar(@RequestBody ValidarCredencialRequest request) {
        return validacionService.validar(request.credencial());
    }
}
