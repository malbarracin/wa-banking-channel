package com.wa.banking.channel.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para aceptación de términos y condiciones del canal (F1).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Aceptación explícita de términos del canal")
public class AcceptTermsRequestV1 {

    @AssertTrue(message = "termsAccepted must be true")
    @Schema(
            description = "Confirmación de aceptación de términos",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private boolean termsAccepted;
}
