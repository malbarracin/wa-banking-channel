package com.wa.banking.channel.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para iniciar re-vinculación tras desvinculación (F4, RN7).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de re-vinculación con nueva verificación")
public class RelinkRequestV1 {

    @AssertTrue(message = "confirmed must be true")
    @Schema(
            description = "Confirmación de inicio de re-vinculación",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private boolean confirmed;
}
