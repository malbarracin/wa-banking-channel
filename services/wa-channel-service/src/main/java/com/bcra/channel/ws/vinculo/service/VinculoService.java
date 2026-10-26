package com.bcra.channel.ws.vinculo.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.channel.ws.vinculo.dto.ActualizarPreferenciasRequest;
import com.bcra.channel.ws.vinculo.dto.HistorialVinculoResponse;
import com.bcra.channel.ws.vinculo.dto.VinculoResponse;
import com.bcra.channel.ws.vinculo.entity.AuditAccion;
import com.bcra.channel.ws.vinculo.entity.AuditResultado;
import com.bcra.channel.ws.vinculo.entity.Preferencias;
import com.bcra.channel.ws.vinculo.entity.Vinculo;
import com.bcra.channel.ws.vinculo.entity.VinculoEstado;
import com.bcra.channel.ws.vinculo.exception.CredencialInvalidaException;
import com.bcra.channel.ws.vinculo.exception.VinculoNoActivoException;
import com.bcra.channel.ws.vinculo.exception.VinculoNotFoundException;
import com.bcra.channel.ws.vinculo.mapper.VinculoMapper;
import com.bcra.channel.ws.vinculo.repository.VinculoRepository;

import lombok.RequiredArgsConstructor;

/** Gestiona el ciclo de vida del vínculo del canal WhatsApp. */
@Service
@RequiredArgsConstructor
public class VinculoService {

    private final VinculoRepository vinculoRepository;
    private final VinculoMapper vinculoMapper;
    private final VinculoAuditService vinculoAuditService;

    /**
     * Consulta el vínculo más recientemente actualizado y todas las interacciones del número para soporte.
     *
     * @param numeroWhatsapp número del canal
     * @return estado actual e historial descendente, incluso para vínculos bloqueados o desvinculados
     * @throws VinculoNotFoundException si no existe ningún registro del número
     */
    @Transactional(readOnly = true)
    public HistorialVinculoResponse consultarHistorial(String numeroWhatsapp) {
        Vinculo vinculo = vinculoRepository
                .findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(numeroWhatsapp)
                .orElseThrow(VinculoNotFoundException::new);
        return vinculoMapper.toHistorialResponse(vinculo, vinculoAuditService.consultarHistorial(numeroWhatsapp));
    }

    /**
     * Registra la aceptación previa a cualquier solicitud de identificación.
     *
     * @param numeroWhatsapp número del canal en formato E.164
     * @return vínculo creado, pendiente de verificación
     */
    @Transactional
    public VinculoResponse aceptarTerminos(String numeroWhatsapp) {
        Vinculo vinculo = new Vinculo();
        vinculo.setNumeroWhatsapp(numeroWhatsapp);
        vinculo.setEstado(VinculoEstado.PENDIENTE_VERIFICACION);
        vinculo.setTerminosAceptados(true);
        vinculo.setFechaAceptacionTerminos(Instant.now());
        Vinculo guardado = vinculoRepository.saveAndFlush(vinculo);
        vinculoAuditService.registrar(AuditAccion.ACEPTACION_TERMINOS, AuditResultado.EXITOSO,
                numeroWhatsapp, null, null, null);
        return vinculoMapper.toResponse(guardado);
    }

    /**
     * Actualiza las preferencias del canal de un vínculo activo con credencial válida.
     *
     * @param numeroWhatsapp número E.164 del vínculo
     * @param documentoTipo tipo de documento propagado por el orquestador tras validar la credencial
     * @param documentoNumero número de documento propagado con la credencial
     * @param request campos a modificar; los null se ignoran
     * @return vínculo con las preferencias confirmadas
     * @throws CredencialInvalidaException si falta el contexto de credencial
     * @throws VinculoNotFoundException si no hay vínculo para el número
     * @throws VinculoNoActivoException si el vínculo no está ACTIVO
     */
    @Transactional
    public VinculoResponse actualizarPreferencias(String numeroWhatsapp, String documentoTipo,
            String documentoNumero, ActualizarPreferenciasRequest request) {
        exigirCredencial(documentoTipo, documentoNumero);
        Vinculo vinculo = vinculoRepository
                .findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(numeroWhatsapp)
                .orElseThrow(VinculoNotFoundException::new);
        if (vinculo.getEstado() != VinculoEstado.ACTIVO) {
            throw new VinculoNoActivoException();
        }
        Preferencias preferencias = vinculo.getPreferencias();
        if (preferencias == null) {
            preferencias = new Preferencias();
            vinculo.setPreferencias(preferencias);
        }
        vinculoMapper.actualizarPreferencias(request, preferencias);
        return vinculoMapper.toResponse(vinculoRepository.saveAndFlush(vinculo));
    }

    private void exigirCredencial(String documentoTipo, String documentoNumero) {
        if (documentoTipo == null || documentoTipo.isBlank()
                || documentoNumero == null || documentoNumero.isBlank()) {
            throw new CredencialInvalidaException();
        }
    }
}
