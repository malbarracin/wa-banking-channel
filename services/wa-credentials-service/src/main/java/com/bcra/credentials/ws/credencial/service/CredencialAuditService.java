package com.bcra.credentials.ws.credencial.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.credentials.ws.credencial.entity.AuditAccion;
import com.bcra.credentials.ws.credencial.entity.AuditResultado;
import com.bcra.credentials.ws.credencial.entity.CredencialAuditLog;
import com.bcra.credentials.ws.credencial.entity.CredencialSesion;
import com.bcra.credentials.ws.credencial.entity.MotivoRevocacion;
import com.bcra.credentials.ws.credencial.repository.CredencialAuditLogRepository;

import lombok.RequiredArgsConstructor;

/** Registra eventos independientes sin conservar texto libre potencialmente sensible. */
@Service
@RequiredArgsConstructor
public class CredencialAuditService {

    private static final int MAX_DOCUMENTO_TIPO_LENGTH = 30;
    private static final int MAX_DOCUMENTO_NUMERO_LENGTH = 50;
    private static final String MOTIVO_PROTEGIDO = "Detalle omitido por seguridad";

    private final CredencialAuditLogRepository auditLogRepository;

    /** Registra el motivo cerrado del rechazo sin aceptar texto libre ni secretos. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarRevocacionRechazada(String numeroWhatsapp, MotivoRevocacion motivo) {
        validarNumero(numeroWhatsapp);
        auditLogRepository.save(CredencialAuditLog.builder().accion(AuditAccion.REVOCACION)
                .resultado(AuditResultado.RECHAZADO).numeroWhatsapp(numeroWhatsapp)
                .motivo(java.util.Objects.requireNonNull(motivo).name()).fecha(Instant.now()).build());
    }

    /** Registra rechazos sin extraer identidad ni identificadores de tokens no verificados. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarValidacionFallida(Optional<CredencialSesion> credencial) {
        var registro = CredencialAuditLog.builder()
                .accion(AuditAccion.VALIDACION_FALLIDA)
                .resultado(AuditResultado.RECHAZADO)
                .motivo("Credencial no válida")
                .fecha(Instant.now());
        credencial.ifPresent(sesion -> registro.numeroWhatsapp(sesion.getNumeroWhatsapp())
                .documentoTipo(sesion.getDocumentoTipo()).documentoNumero(sesion.getDocumentoNumero())
                .jti(sesion.getJti()));
        auditLogRepository.save(registro.build());
    }

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
    public CredencialAuditLog registrar(AuditAccion accion, AuditResultado resultado, String numeroWhatsapp,
            String documentoTipo, String documentoNumero, String motivo) {
        if (accion == null || resultado == null) {
            throw new IllegalArgumentException("La acción y el resultado son obligatorios");
        }
        validarNumero(numeroWhatsapp);
        validarReferencia(documentoTipo, MAX_DOCUMENTO_TIPO_LENGTH);
        validarReferencia(documentoNumero, MAX_DOCUMENTO_NUMERO_LENGTH);

        CredencialAuditLog registro = CredencialAuditLog.builder()
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
    public List<CredencialAuditLog> consultarHistorial(String numeroWhatsapp) {
        validarNumero(numeroWhatsapp);
        return List.copyOf(auditLogRepository.findByNumeroWhatsappOrderByFechaDesc(numeroWhatsapp));
    }

    private void validarNumero(String numeroWhatsapp) {
        if (numeroWhatsapp == null || !numeroWhatsapp.matches("\\+[1-9][0-9]{1,14}")) {
            throw new IllegalArgumentException("El número de WhatsApp debe estar en formato E.164");
        }
    }

    private void validarReferencia(String referencia, int longitudMaxima) {
        if (referencia != null && (referencia.isBlank() || referencia.length() > longitudMaxima)) {
            throw new IllegalArgumentException("La referencia documental está vacía o excede la longitud permitida");
        }
    }
}
