package com.bcra.channel.ws.vinculo.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.channel.ws.vinculo.dto.VinculoResponse;
import com.bcra.channel.ws.vinculo.dto.HistorialVinculoResponse;
import com.bcra.channel.ws.vinculo.exception.VinculoNotFoundException;
import com.bcra.channel.ws.vinculo.entity.AuditAccion;
import com.bcra.channel.ws.vinculo.entity.AuditResultado;
import com.bcra.channel.ws.vinculo.entity.Vinculo;
import com.bcra.channel.ws.vinculo.entity.VinculoEstado;
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
}
