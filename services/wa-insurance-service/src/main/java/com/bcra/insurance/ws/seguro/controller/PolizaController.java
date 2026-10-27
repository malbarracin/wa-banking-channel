package com.bcra.insurance.ws.seguro.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.insurance.ws.seguro.dto.CoberturaResponse;
import com.bcra.insurance.ws.seguro.dto.PolizaResponse;
import com.bcra.insurance.ws.seguro.service.CoberturaService;
import com.bcra.insurance.ws.seguro.service.PolizaService;

import lombok.RequiredArgsConstructor;

/** Consultas de seguros con credencial de sesión de H2. */
@RestController
@RequestMapping("/api/v1/seguros")
@RequiredArgsConstructor
public class PolizaController {

    private final PolizaService polizas;
    private final CoberturaService coberturas;

    /** @return listado de pólizas vigentes autorizadas; sin credencial responde 401 */
    @GetMapping
    public List<PolizaResponse> listar(
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial) {
        return polizas.listar(credencial);
    }

    /** @return resumen de condiciones; 403 para terceros y 404 para pólizas inexistentes */
    @GetMapping("/{polizaId}/coberturas")
    public CoberturaResponse consultarCoberturas(
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @PathVariable UUID polizaId) {
        return coberturas.consultar(credencial, polizaId);
    }
}
