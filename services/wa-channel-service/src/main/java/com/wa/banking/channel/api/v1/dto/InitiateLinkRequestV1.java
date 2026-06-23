package com.wa.banking.channel.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para iniciar el vínculo de un número WhatsApp (F1).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos para iniciar vínculo de canal WhatsApp")
public class InitiateLinkRequestV1 {

    @NotBlank(message = "phoneNumber is required")
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "phoneNumber must be in E.164 format")
    @Schema(
            description = "Número WhatsApp en formato E.164",
            example = "+541112345678",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String phoneNumber;
}
