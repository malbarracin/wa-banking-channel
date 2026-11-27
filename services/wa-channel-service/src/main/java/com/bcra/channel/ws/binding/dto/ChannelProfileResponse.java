package com.bcra.channel.ws.binding.dto;

import java.time.Instant;
import java.util.UUID;

import com.bcra.channel.ws.binding.entity.BindingStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Perfil del canal para un vínculo activo: datos públicos y preferencias.
 * No incluye datos legales sensibles (documentos ni contactos completos).
 *
 * @param whatsappNumber        número en E.164
 * @param clientId              cliente vinculado en el registro del banco
 * @param displayName           nombre para mostrar obtenido de H1
 * @param bindingStatus         estado del vínculo (siempre {@link BindingStatus#ACTIVO} en respuesta exitosa)
 * @param languagePreference    código de idioma preferido
 * @param notificationsEnabled  si las notificaciones del canal están activas
 * @param createdAt             alta del vínculo
 * @param updatedAt             última actualización del vínculo
 */
@Schema(description = "Perfil del canal (sin datos legales sensibles)")
public record ChannelProfileResponse(
        @Schema(description = "Número WhatsApp en E.164", example = "+5491112345678")
        String whatsappNumber,
        @Schema(description = "Identificador interno del cliente", example = "c1d2e3f4-a5b6-7890-cdef-1234567890ab")
        UUID clientId,
        @Schema(description = "Nombre para mostrar del cliente", example = "Ana Pérez")
        String displayName,
        @Schema(description = "Estado del vínculo", example = "ACTIVO")
        BindingStatus bindingStatus,
        @Schema(description = "Preferencia de idioma (código ISO)", example = "es")
        String languagePreference,
        @Schema(description = "Notificaciones del canal habilitadas", example = "true")
        boolean notificationsEnabled,
        @Schema(description = "Alta del vínculo (ISO-8601 UTC)", example = "2026-11-27T12:00:00Z")
        Instant createdAt,
        @Schema(description = "Última actualización (ISO-8601 UTC)", example = "2026-11-27T12:30:00Z")
        Instant updatedAt) {
}
