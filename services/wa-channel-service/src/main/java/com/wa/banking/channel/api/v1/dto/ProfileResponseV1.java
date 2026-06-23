package com.wa.banking.channel.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Perfil del canal sin datos legales sensibles (RN6).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Perfil del canal WhatsApp (sin datos legales sensibles)")
public class ProfileResponseV1 {

    @Schema(description = "Identificador del vínculo", example = "665f1a2b3c4d5e6f7a8b9c0d")
    private String linkId;

    @Schema(description = "Nombre para mostrar", example = "John Doe")
    private String displayName;

    @Schema(description = "Correo electrónico enmascarado", example = "j***@example.com")
    private String maskedEmail;

    @Schema(description = "Teléfono de contacto enmascarado", example = "+5411****5678")
    private String maskedPhone;

    @Schema(description = "Idioma preferido", example = "es")
    private String language;
}
