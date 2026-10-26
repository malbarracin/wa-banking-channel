package com.bcra.channel.ws.vinculo.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.channel.ws.vinculo.CredencialCanal;
import com.bcra.channel.ws.vinculo.dto.AceptarTerminosRequest;
import com.bcra.channel.ws.vinculo.dto.BloquearVinculoRequest;
import com.bcra.channel.ws.vinculo.dto.DesvincularRequest;
import com.bcra.channel.ws.vinculo.dto.HistorialVinculoResponse;
import com.bcra.channel.ws.vinculo.dto.PerfilCanalResponse;
import com.bcra.channel.ws.vinculo.dto.VerificacionResultadoResponse;
import com.bcra.channel.ws.vinculo.dto.VerificarIdentidadRequest;
import com.bcra.channel.ws.vinculo.dto.VinculoEstadoResponse;
import com.bcra.channel.ws.vinculo.dto.VinculoResponse;
import com.bcra.channel.ws.vinculo.service.VerificacionService;
import com.bcra.channel.ws.vinculo.service.VinculoService;

import lombok.RequiredArgsConstructor;

/** API del vínculo al canal WhatsApp. */
@RestController
@RequestMapping("/api/v1/vinculos")
@RequiredArgsConstructor
public class VinculoController {

    private final VinculoService vinculoService;
    private final VerificacionService verificacionService;

    /**
     * Verifica identidad después de aceptar términos.
     * @param numero número E.164
     * @param request pruebas de identidad
     * @return resultado de la verificación, sin credencial de sesión
     */
    @PostMapping("/{numero}/verificaciones")
    public VerificacionResultadoResponse verificar(@PathVariable String numero,
            @Valid @RequestBody VerificarIdentidadRequest request) {
        return verificacionService.verificar(numero, request);
    }

    /**
     * Consulta anti-duplicados: si el número ya tiene vínculo, sin datos del cliente.
     * @param numero número de WhatsApp consultado
     * @return 200 con vinculado true/false; nunca 404
     */
    @GetMapping("/{numero}/estado")
    public VinculoEstadoResponse consultarEstado(@PathVariable String numero) {
        return vinculoService.consultarEstado(numero);
    }

    /**
     * Perfil permitido del canal para el titular con credencial válida y vínculo activo.
     * @param numero número de WhatsApp
     * @param credencialValida header {@link CredencialCanal#HEADER_CREDENCIAL_VALIDA} propagado aguas arriba
     * @return 200 con datos de H1 + preferencias locales; 401/409 según contrato
     */
    @GetMapping("/{numero}/perfil")
    public PerfilCanalResponse consultarPerfil(@PathVariable String numero,
            @RequestHeader(value = CredencialCanal.HEADER_CREDENCIAL_VALIDA, required = false)
            String credencialValida) {
        return vinculoService.consultarPerfil(numero, credencialValida);
    }

    /**
     * Devuelve el historial resumido para uso interno de soporte del banco.
     * @param numero número de WhatsApp consultado
     * @return 200 con estado actual y eventos descendentes; 404 si no hay vínculo registrado
     */
    @GetMapping("/{numero}/historial")
    public HistorialVinculoResponse consultarHistorial(@PathVariable String numero) {
        return vinculoService.consultarHistorial(numero);
    }

    /**
     * Confirma la aceptación explícita de los términos del canal.
     * @param request número de WhatsApp, sin datos de identificación
     * @return 201 con el vínculo pendiente de verificación
     */
    @PostMapping("/terminos")
    public ResponseEntity<VinculoResponse> aceptarTerminos(@Valid @RequestBody AceptarTerminosRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vinculoService.aceptarTerminos(request.numeroWhatsapp()));
    }

    /**
     * Bloquea el vínculo (cliente o política del banco) y solicita revocación a H2.
     * @param numero número de WhatsApp
     * @param request motivo y confirmación
     * @param credencialValida obligatorio si el motivo es solicitud del cliente
     * @return 200 con estado {@code BLOQUEADO}
     */
    @PatchMapping("/{numero}/bloqueo")
    public VinculoResponse bloquear(@PathVariable String numero,
            @Valid @RequestBody BloquearVinculoRequest request,
            @RequestHeader(value = CredencialCanal.HEADER_CREDENCIAL_VALIDA, required = false)
            String credencialValida) {
        return vinculoService.bloquear(numero, request, credencialValida);
    }

    /**
     * Desvincula el canal a pedido del cliente y solicita revocación a H2.
     * @param numero número de WhatsApp
     * @param request confirmación explícita
     * @param credencialValida contexto de credencial válida obligatorio
     * @return 200 con estado {@code DESVINCULADO}
     */
    @PatchMapping("/{numero}/desvinculacion")
    public VinculoResponse desvincular(@PathVariable String numero,
            @Valid @RequestBody DesvincularRequest request,
            @RequestHeader(value = CredencialCanal.HEADER_CREDENCIAL_VALIDA, required = false)
            String credencialValida) {
        return vinculoService.desvincular(numero, request, credencialValida);
    }
}
