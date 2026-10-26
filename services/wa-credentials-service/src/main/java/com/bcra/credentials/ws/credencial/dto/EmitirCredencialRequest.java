package com.bcra.credentials.ws.credencial.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Confirmación del canal H3; ambos indicadores deben ser verdaderos para autorizar la emisión. */
@Schema(description = "Confirmación del canal H3; ambos indicadores deben ser verdaderos para autorizar la emisión.")
public record EmitirCredencialRequest(
        @Schema(description = "Número de WhatsApp en formato E.164",
                example = "+5491111111111", requiredMode = Schema.RequiredMode.REQUIRED,
                pattern = "\\+[1-9][0-9]{1,14}")
        @NotBlank @Pattern(regexp = "\\+[1-9][0-9]{1,14}") String numeroWhatsapp,
        @Schema(description = "Tipo documental del cliente en H1",
                example = "DNI", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 30)
        @NotBlank @Size(max = 30) String documentoTipo,
        @Schema(description = "Número documental del cliente en H1",
                example = "12345678", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 50)
        @NotBlank @Size(max = 50) String documentoNumero,
        @Schema(description = "Confirmación de H3 de vínculo activo; debe ser true para emitir",
                example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Boolean vinculoActivo,
        @Schema(description = "Confirmación de identidad verificada por H3; debe ser true para emitir",
                example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Boolean identidadVerificada) {

    @Override
    public String toString() {
        return "EmitirCredencialRequest[protegido]";
    }
}
