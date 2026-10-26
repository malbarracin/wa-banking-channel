package com.bcra.channel.ws.vinculo.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.channel.ws.vinculo.CredencialCanal;
import com.bcra.channel.ws.vinculo.client.ClientePerfilResponse;
import com.bcra.channel.ws.vinculo.client.UsersServiceClient;
import com.bcra.channel.ws.vinculo.dto.HistorialVinculoResponse;
import com.bcra.channel.ws.vinculo.dto.PerfilCanalResponse;
import com.bcra.channel.ws.vinculo.dto.VinculoEstadoResponse;
import com.bcra.channel.ws.vinculo.dto.VinculoResponse;
import com.bcra.channel.ws.vinculo.entity.AuditAccion;
import com.bcra.channel.ws.vinculo.entity.AuditResultado;
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
    private final UsersServiceClient usersServiceClient;

    /**
     * Informa si el número ya tiene vínculo (anti-duplicados), sin exponer datos del cliente.
     * @param numeroWhatsapp número del canal
     * @return siempre presente: {@code vinculado=false} si no hay registro (nunca 404)
     */
    @Transactional(readOnly = true)
    public VinculoEstadoResponse consultarEstado(String numeroWhatsapp) {
        Optional<Vinculo> vinculo = vinculoRepository
                .findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(numeroWhatsapp);
        if (vinculo.isEmpty()) {
            return new VinculoEstadoResponse(numeroWhatsapp, false, null);
        }
        return vinculoMapper.toEstadoResponse(vinculo.get());
    }

    /**
     * Combina preferencias locales con el perfil permitido de H1 para un vínculo activo.
     * @param numeroWhatsapp número del canal
     * @param valorHeaderCredencial valor del header {@link CredencialCanal#HEADER_CREDENCIAL_VALIDA}
     * @return perfil del canal sin datos legales sensibles
     * @throws CredencialInvalidaException si falta el contexto de credencial válida
     * @throws VinculoNoActivoException si no hay vínculo ACTIVO
     */
    @Transactional(readOnly = true)
    public PerfilCanalResponse consultarPerfil(String numeroWhatsapp, String valorHeaderCredencial) {
        if (!CredencialCanal.esContextoValido(valorHeaderCredencial)) {
            throw new CredencialInvalidaException();
        }
        List<Vinculo> activos = vinculoRepository.findByNumeroWhatsappAndEstado(
                numeroWhatsapp, VinculoEstado.ACTIVO);
        if (activos.isEmpty()) {
            throw new VinculoNoActivoException();
        }
        Vinculo vinculo = activos.getFirst();
        ClientePerfilResponse perfilH1 = usersServiceClient.consultarPerfil(
                vinculo.getDocumentoTipo(), vinculo.getDocumentoNumero());
        return vinculoMapper.toPerfilCanalResponse(perfilH1, vinculo.getPreferencias());
    }

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
