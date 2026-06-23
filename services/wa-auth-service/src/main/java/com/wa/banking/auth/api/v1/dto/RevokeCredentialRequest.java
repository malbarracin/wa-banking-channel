package com.wa.banking.auth.api.v1.dto;

import com.wa.banking.auth.entity.RevokeReason;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud de revocación de credencial con motivo explícito (banco/riesgo).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de revocación con motivo")
public class RevokeCredentialRequest {

    @Schema(description = "Motivo de revocación", example = "FRAUD")
    private RevokeReason reason;
}
