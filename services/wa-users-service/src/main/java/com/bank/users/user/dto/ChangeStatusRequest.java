package com.bank.users.user.dto;

import com.bank.users.user.entity.UserStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de cambio de estado operativo ({@code PATCH /api/v1/users/{id}/status}).
 *
 * <p>Transiciones válidas (OQ-5): {@code ACTIVE → SUSPENDED}, {@code ACTIVE → INACTIVE},
 * {@code SUSPENDED → ACTIVE} y {@code SUSPENDED → INACTIVE}. {@code INACTIVE} (baja lógica) es terminal.</p>
 *
 * @param status estado destino (obligatorio; ausente → 400 {@code VALIDATION_ERROR}, valor desconocido →
 *               400 {@code BAD_REQUEST})
 * @param reason motivo informativo (opcional, máx. {@value #REASON_MAX_LENGTH} caracteres). No se persiste en
 *               auditoría para evitar texto libre con datos personales.
 */
public record ChangeStatusRequest(
        @NotNull UserStatus status,
        @Size(max = ChangeStatusRequest.REASON_MAX_LENGTH) String reason) {

    /** Longitud máxima admitida para {@code reason}. */
    public static final int REASON_MAX_LENGTH = 255;

    /**
     * {@code reason} se omite de {@code toString()} por poder contener texto libre.
     *
     * @return representación sin el motivo
     */
    @Override
    public String toString() {
        return "ChangeStatusRequest[status=" + status + "]";
    }
}
