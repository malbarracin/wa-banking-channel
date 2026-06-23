package com.wa.banking.channel.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para bloquear un vínculo de canal (F3).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Confirmación de bloqueo del vínculo")
public class BlockLinkRequestV1 {

    @AssertTrue(message = "confirmed must be true")
    @Schema(
            description = "Confirmación explícita de bloqueo",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private boolean confirmed;
}
