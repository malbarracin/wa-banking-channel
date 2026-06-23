package com.wa.banking.accounts.api.error;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO unificado de respuesta de error expuesto por la API REST.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Contrato unificado de respuesta de error")
public class ErrorResponse {

    @Schema(
            description = "Código de error estandarizado",
            example = "VALIDATION_ERROR",
            allowableValues = {"NOT_FOUND", "VALIDATION_ERROR", "BAD_REQUEST", "INTERNAL_ERROR", "UNAUTHORIZED"}
    )
    private String code;

    @Schema(description = "Descripción legible del error", example = "Validation failed")
    private String message;

    @Schema(
            description = "Detalles adicionales (ej. errores de campo en validación)",
            example = "[\"credentialId: credentialId is required\"]"
    )
    private List<String> details;

    @Schema(description = "Marca de tiempo ISO-8601", example = "2026-06-23T10:30:00Z")
    private Instant timestamp;
}
