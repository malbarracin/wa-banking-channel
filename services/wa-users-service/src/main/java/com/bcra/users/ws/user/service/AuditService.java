package com.bcra.users.ws.user.service;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.users.ws.user.entity.AuditAction;
import com.bcra.users.ws.user.entity.AuditLog;
import com.bcra.users.ws.user.entity.AuditResult;
import com.bcra.users.ws.user.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;

/** Soporte interno para registrar operaciones sin depender de la existencia del usuario. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int MAX_TEXT_LENGTH = 255;

    private final AuditLogRepository auditLogRepository;

    /**
     * Persiste la auditoría en una transacción independiente de la operación principal.
     * Debe invocarse a través del bean Spring al finalizar la operación.
     *
     * @param accion operación realizada
     * @param resultado resultado de la operación
     * @param tipoDocumento tipo de documento, libre según la política del banco
     * @param numeroDocumento número que identifica al usuario aunque el alta haya sido rechazada
     * @param motivo descripción breve sin datos sensibles; vacío para operaciones exitosas
     * @return registro persistido con identificador y fecha UTC
     * @throws IllegalArgumentException si falta la referencia o el motivo de un rechazo
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog registrar(AuditAction accion, AuditResult resultado, String tipoDocumento,
            String numeroDocumento, String motivo) {
        Objects.requireNonNull(accion, "accion");
        Objects.requireNonNull(resultado, "resultado");
        validarTexto(tipoDocumento, "tipoDocumento");
        validarTexto(numeroDocumento, "numeroDocumento");
        Objects.requireNonNull(motivo, "motivo");
        if (resultado == AuditResult.RECHAZADO) {
            validarTexto(motivo, "motivo");
        } else if (motivo.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("El motivo supera la longitud permitida");
        }

        AuditLog registro = AuditLog.builder()
                .tipoDocumento(tipoDocumento)
                .numeroDocumento(numeroDocumento)
                .fecha(Instant.now())
                .accion(accion)
                .resultado(resultado)
                .motivo(motivo)
                .build();
        return auditLogRepository.save(registro);
    }

    private void validarTexto(String texto, String campo) {
        if (texto == null || texto.isBlank() || texto.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio y admite hasta 255 caracteres");
        }
    }
}
