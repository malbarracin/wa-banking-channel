package com.bcra.channel.ws.vinculo.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.channel.ws.vinculo.entity.AuditAccion;
import com.bcra.channel.ws.vinculo.entity.AuditResultado;
import com.bcra.channel.ws.vinculo.entity.VinculoAuditLog;
import com.bcra.channel.ws.vinculo.repository.VinculoAuditLogRepository;

import lombok.RequiredArgsConstructor;

/** Registra eventos independientes sin conservar texto libre potencialmente sensible. */
@Service
@RequiredArgsConstructor
public class VinculoAuditService {

    private static final int MAX_TEXT_LENGTH = 255;
    private static final String MOTIVO_PROTEGIDO = "Detalle omitido por seguridad";

    private final VinculoAuditLogRepository auditLogRepository;

    /**
     * Persiste un evento aunque la operación llamadora se revierta; invocar mediante el bean Spring.
     * El motivo libre se sustituye por un texto fijo: no se intenta detectar secretos por palabras clave.
     *
     * @param accion acción realizada, obligatoria
     * @param resultado resultado obligatorio
     * @param numeroWhatsapp número E.164 del canal
     * @param documentoTipo tipo de documento opcional, exclusivamente identificatorio
     * @param documentoNumero número de documento opcional, exclusivamente identificatorio
     * @param motivo detalle opcional; su contenido nunca se persiste ni se registra en logs
     * @return evento persistido con identificador y fecha UTC
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VinculoAuditLog registrar(AuditAccion accion, AuditResultado resultado, String numeroWhatsapp,
            String documentoTipo, String documentoNumero, String motivo) {
        if (accion == null || resultado == null) {
            throw new IllegalArgumentException("La acción y el resultado son obligatorios");
        }
        validarNumero(numeroWhatsapp);
        validarReferencia(documentoTipo);
        validarReferencia(documentoNumero);

        VinculoAuditLog registro = VinculoAuditLog.builder()
                .accion(accion)
                .resultado(resultado)
                .numeroWhatsapp(numeroWhatsapp)
                .documentoTipo(documentoTipo)
                .documentoNumero(documentoNumero)
                .motivo(motivo == null || motivo.isBlank() ? null : MOTIVO_PROTEGIDO)
                .fecha(Instant.now())
                .build();
        return auditLogRepository.save(registro);
    }

    /**
     * Consulta eventos del número, del más reciente al más antiguo.
     * @param numeroWhatsapp número E.164 del canal
     * @return lista inmodificable, vacía si no hay eventos
     */
    @Transactional(readOnly = true)
    public List<VinculoAuditLog> consultarHistorial(String numeroWhatsapp) {
        validarNumero(numeroWhatsapp);
        return List.copyOf(auditLogRepository.findByNumeroWhatsappOrderByFechaDesc(numeroWhatsapp));
    }

    private void validarNumero(String numeroWhatsapp) {
        if (numeroWhatsapp == null || !numeroWhatsapp.matches("\\+[1-9][0-9]{1,14}")) {
            throw new IllegalArgumentException("El número de WhatsApp debe estar en formato E.164");
        }
    }

    private void validarReferencia(String referencia) {
        if (referencia != null && (referencia.isBlank() || referencia.length() > MAX_TEXT_LENGTH)) {
            throw new IllegalArgumentException("La referencia documental admite entre 1 y 255 caracteres");
        }
    }
}
