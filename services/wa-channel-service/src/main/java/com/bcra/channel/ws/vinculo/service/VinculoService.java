package com.bcra.channel.ws.vinculo.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.channel.ws.vinculo.CredencialCanal;
import com.bcra.channel.ws.vinculo.client.ClientePerfilResponse;
import com.bcra.channel.ws.vinculo.client.CredencialesServiceClient;
import com.bcra.channel.ws.vinculo.client.UsersServiceClient;
import com.bcra.channel.ws.vinculo.dto.ActualizarPreferenciasRequest;
import com.bcra.channel.ws.vinculo.dto.BloquearVinculoRequest;
import com.bcra.channel.ws.vinculo.dto.CanalCredentialToken;
import com.bcra.channel.ws.vinculo.dto.DesvincularRequest;
import com.bcra.channel.ws.vinculo.dto.HistorialVinculoResponse;
import com.bcra.channel.ws.vinculo.dto.PerfilCanalResponse;
import com.bcra.channel.ws.vinculo.dto.VinculoEstadoResponse;
import com.bcra.channel.ws.vinculo.dto.VinculoResponse;
import com.bcra.channel.ws.vinculo.entity.AuditAccion;
import com.bcra.channel.ws.vinculo.entity.AuditResultado;
import com.bcra.channel.ws.vinculo.entity.MotivoBloqueo;
import com.bcra.channel.ws.vinculo.entity.Preferencias;
import com.bcra.channel.ws.vinculo.entity.Vinculo;
import com.bcra.channel.ws.vinculo.entity.VinculoEstado;
import com.bcra.channel.ws.vinculo.exception.CredencialInvalidaException;
import com.bcra.channel.ws.vinculo.exception.NumeroYaVinculadoException;
import com.bcra.channel.ws.vinculo.exception.VinculoEstadoConflictoException;
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
    private final CredencialesServiceClient credencialesServiceClient;
    private final CanalCredentialValidator canalCredentialValidator;

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
     * @param valorHeaderCredencial valor del header {@link CredencialCanal#HEADER_CREDENCIAL_TOKEN}
     * @return perfil del canal sin datos legales sensibles
     * @throws CredencialInvalidaException si falta el contexto de credencial válida
     * @throws VinculoNoActivoException si no hay vínculo ACTIVO
     */
    @Transactional(readOnly = true)
    public PerfilCanalResponse consultarPerfil(String numeroWhatsapp, String valorHeaderCredencial) {
        if (!canalCredentialValidator.validar(new CanalCredentialToken(valorHeaderCredencial), numeroWhatsapp)) {
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
     * Si ya hay un pendiente para el número, reutiliza ese registro (idempotente).
     * Si el número ya tiene vínculo activo, rechaza con conflicto.
     *
     * @param numeroWhatsapp número del canal en formato E.164
     * @return vínculo pendiente de verificación (creado o reutilizado)
     * @throws NumeroYaVinculadoException si el número ya tiene un vínculo {@code ACTIVO}
     */
    @Transactional
    public VinculoResponse aceptarTerminos(String numeroWhatsapp) {
        List<Vinculo> existentes = vinculoRepository.bloquearPorNumero(numeroWhatsapp);
        if (existentes.stream().anyMatch(v -> v.getEstado() == VinculoEstado.ACTIVO)) {
            vinculoAuditService.registrar(AuditAccion.ACEPTACION_TERMINOS, AuditResultado.RECHAZADO,
                    numeroWhatsapp, null, null, null);
            throw new NumeroYaVinculadoException();
        }
        Optional<Vinculo> pendiente = existentes.stream()
                .filter(v -> v.getEstado() == VinculoEstado.PENDIENTE_VERIFICACION)
                .findFirst();
        if (pendiente.isPresent()) {
            Vinculo existente = pendiente.get();
            existente.setTerminosAceptados(true);
            existente.setFechaAceptacionTerminos(Instant.now());
            Vinculo reutilizado = vinculoRepository.saveAndFlush(existente);
            vinculoAuditService.registrar(AuditAccion.ACEPTACION_TERMINOS, AuditResultado.EXITOSO,
                    numeroWhatsapp, null, null, null);
            return vinculoMapper.toResponse(reutilizado);
        }
        Vinculo vinculo = new Vinculo();
        vinculo.setNumeroWhatsapp(numeroWhatsapp);
        vinculo.setEstado(VinculoEstado.PENDIENTE_VERIFICACION);
        vinculo.setTerminosAceptados(true);
        vinculo.setFechaAceptacionTerminos(Instant.now());
        try {
            Vinculo guardado = vinculoRepository.saveAndFlush(vinculo);
            vinculoAuditService.registrar(AuditAccion.ACEPTACION_TERMINOS, AuditResultado.EXITOSO,
                    numeroWhatsapp, null, null, null);
            return vinculoMapper.toResponse(guardado);
        } catch (DataIntegrityViolationException exception) {
            vinculoAuditService.registrar(AuditAccion.ACEPTACION_TERMINOS, AuditResultado.RECHAZADO,
                    numeroWhatsapp, null, null, null);
            throw new NumeroYaVinculadoException();
        }
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

    /**
     * Bloquea el vínculo a pedido del cliente o por política del banco y solicita revocación a H2.
     * @param numeroWhatsapp número del canal
     * @param request motivo y confirmación explícita
     * @param valorHeaderCredencial header de credencial; obligatorio si el motivo es {@link MotivoBloqueo#SOLICITUD_CLIENTE}
     * @return vínculo en estado {@link VinculoEstado#BLOQUEADO}
     */
    @Transactional
    public VinculoResponse bloquear(String numeroWhatsapp, BloquearVinculoRequest request,
            String valorHeaderCredencial) {
        if (request.motivo() == MotivoBloqueo.SOLICITUD_CLIENTE
                && !canalCredentialValidator.validar(
                        new CanalCredentialToken(valorHeaderCredencial), numeroWhatsapp)) {
            throw new CredencialInvalidaException();
        }
        Vinculo vinculo = buscarVinculo(numeroWhatsapp);
        if (vinculo.getEstado() != VinculoEstado.ACTIVO) {
            throw new VinculoEstadoConflictoException();
        }
        Instant ahora = Instant.now();
        vinculo.setEstado(VinculoEstado.BLOQUEADO);
        vinculo.setMotivoBloqueo(request.motivo());
        vinculo.setFechaUltimoCambioEstado(ahora);
        Vinculo guardado = vinculoRepository.saveAndFlush(vinculo);
        vinculoAuditService.registrar(AuditAccion.BLOQUEO, AuditResultado.EXITOSO, numeroWhatsapp,
                guardado.getDocumentoTipo(), guardado.getDocumentoNumero(), request.motivo().name());
        solicitarRevocacionBestEffort(numeroWhatsapp, request.motivo().name(), AuditAccion.BLOQUEO,
                guardado.getDocumentoTipo(), guardado.getDocumentoNumero());
        return vinculoMapper.toResponse(guardado);
    }

    /**
     * Desvincula el canal a pedido del cliente y solicita revocación a H2.
     * @param numeroWhatsapp número del canal
     * @param request confirmación explícita
     * @param valorHeaderCredencial header de credencial válida obligatorio
     * @return vínculo en estado {@link VinculoEstado#DESVINCULADO}
     */
    @Transactional
    public VinculoResponse desvincular(String numeroWhatsapp, DesvincularRequest request,
            String valorHeaderCredencial) {
        if (!canalCredentialValidator.validar(new CanalCredentialToken(valorHeaderCredencial), numeroWhatsapp)) {
            throw new CredencialInvalidaException();
        }
        Vinculo vinculo = buscarVinculo(numeroWhatsapp);
        if (vinculo.getEstado() != VinculoEstado.ACTIVO && vinculo.getEstado() != VinculoEstado.BLOQUEADO) {
            throw new VinculoEstadoConflictoException();
        }
        Instant ahora = Instant.now();
        vinculo.setEstado(VinculoEstado.DESVINCULADO);
        vinculo.setFechaUltimoCambioEstado(ahora);
        Vinculo guardado = vinculoRepository.saveAndFlush(vinculo);
        vinculoAuditService.registrar(AuditAccion.DESVINCULACION, AuditResultado.EXITOSO, numeroWhatsapp,
                guardado.getDocumentoTipo(), guardado.getDocumentoNumero(), null);
        solicitarRevocacionBestEffort(numeroWhatsapp, MotivoBloqueo.SOLICITUD_CLIENTE.name(),
                AuditAccion.DESVINCULACION, guardado.getDocumentoTipo(), guardado.getDocumentoNumero());
        return vinculoMapper.toResponse(guardado);
    }

    private void exigirCredencial(String documentoTipo, String documentoNumero) {
        if (documentoTipo == null || documentoTipo.isBlank()
                || documentoNumero == null || documentoNumero.isBlank()) {
            throw new CredencialInvalidaException();
        }
    }

    private Vinculo buscarVinculo(String numeroWhatsapp) {
        return vinculoRepository.findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(numeroWhatsapp)
                .orElseThrow(VinculoNotFoundException::new);
    }

    /**
     * Invoca H2 sin revertir el cambio local; si falla, audita el fallo de la solicitud de revocación.
     */
    private void solicitarRevocacionBestEffort(String numeroWhatsapp, String motivo, AuditAccion accion,
            String documentoTipo, String documentoNumero) {
        boolean confirmada = credencialesServiceClient.solicitarRevocacion(numeroWhatsapp, motivo);
        if (!confirmada) {
            vinculoAuditService.registrar(accion, AuditResultado.RECHAZADO, numeroWhatsapp,
                    documentoTipo, documentoNumero, "Fallo solicitud revocacion H2");
        }
    }
}
