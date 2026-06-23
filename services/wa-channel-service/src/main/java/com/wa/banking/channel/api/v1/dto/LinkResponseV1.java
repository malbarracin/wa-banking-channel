package com.wa.banking.channel.api.v1.dto;

import com.wa.banking.channel.entity.LinkStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Respuesta del estado de un vínculo de canal WhatsApp.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Estado del vínculo de canal WhatsApp")
public class LinkResponseV1 {

    @Schema(description = "Identificador del vínculo", example = "665f1a2b3c4d5e6f7a8b9c0d")
    private String id;

    @Schema(description = "Número WhatsApp en formato E.164", example = "+541112345678")
    private String phoneNumber;

    @Schema(description = "Identificador del usuario bancario vinculado", example = "user-bank-001")
    private String bankUserId;

    @Schema(description = "Estado operativo del vínculo", example = "ACTIVE")
    private LinkStatus status;

    @Schema(description = "Indica si la identidad fue verificada")
    private boolean identityVerified;

    @Schema(description = "Fecha de aceptación de términos")
    private Instant termsAcceptedAt;

    @Schema(description = "Intentos de verificación realizados")
    private int verificationAttempts;

    @Schema(description = "Bloqueo temporal de verificación hasta")
    private Instant verificationBlockedUntil;

    @Schema(description = "Fecha de creación")
    private Instant createdAt;

    @Schema(description = "Fecha de última actualización")
    private Instant updatedAt;
}
