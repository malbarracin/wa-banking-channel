package com.bcra.insurance.ws.seguro.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.insurance.ws.seguro.dto.DocumentacionPendienteResponse;
import com.bcra.insurance.ws.seguro.dto.SiniestroResponse;
import com.bcra.insurance.ws.seguro.service.DocumentacionService;
import com.bcra.insurance.ws.seguro.service.SiniestroService;

import lombok.RequiredArgsConstructor;

/** Seguimiento de siniestros y documentación pendiente con credencial de sesión de H2. */
@RestController
@RequestMapping("/api/v1/siniestros")
@RequiredArgsConstructor
public class SiniestroController {

    private final SiniestroService siniestros;
    private final DocumentacionService documentacion;

    /**
     * Refleja el estado del sistema de siniestros para un caso propio.
     *
     * @param credencial JWT de sesión en {@code X-Credencial-Sesion}
     * @param numeroCaso identificador del caso
     * @return estado y pendiente; 401/403/404 según credencial, titularidad o existencia
     */
    @GetMapping("/{numeroCaso}")
    public SiniestroResponse consultar(
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @PathVariable String numeroCaso) {
        return siniestros.consultar(credencial, numeroCaso);
    }

    /**
     * Recordatorio de documentación pendiente con lista simple y orientación formal.
     *
     * @param credencial JWT de sesión en {@code X-Credencial-Sesion}
     * @param numeroCaso identificador del caso
     * @return documentos pendientes; 401/403/404 según credencial, titularidad o existencia
     */
    @GetMapping("/{numeroCaso}/documentacion-pendiente")
    public DocumentacionPendienteResponse documentacionPendiente(
            @RequestHeader(value = "X-Credencial-Sesion", required = false) String credencial,
            @PathVariable String numeroCaso) {
        return documentacion.listarPendiente(credencial, numeroCaso);
    }
}
