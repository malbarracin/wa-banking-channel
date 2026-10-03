package com.bank.users.user.dto;

/**
 * Resultado de la verificación de elegibilidad de un usuario para iniciar un nuevo vínculo de canal.
 *
 * <p>Expone exclusivamente {@code userId} y {@code eligible}: no incluye estado, datos personales,
 * auditoría, historial ni datos de verificación del canal.</p>
 *
 * @param userId   identificador interno del usuario consultado
 * @param eligible {@code true} únicamente si el usuario está {@code ACTIVE};
 *                 {@code false} si está {@code SUSPENDED} o {@code INACTIVE}
 */
public record EligibilityResponse(Long userId, boolean eligible) {
}
