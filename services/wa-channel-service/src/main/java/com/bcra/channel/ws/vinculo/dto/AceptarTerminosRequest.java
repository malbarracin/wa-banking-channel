package com.bcra.channel.ws.vinculo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import io.swagger.v3.oas.annotations.media.Schema;

/** Aceptación explícita de términos; no requiere datos de identificación del cliente. */
@Schema(description = "Aceptación de términos del canal; solo el número, sin datos de identificación")
public record AceptarTerminosRequest(
        @NotBlank(message = "El número de WhatsApp es obligatorio")
        @Pattern(regexp = "^\\+[1-9][0-9]{1,14}$", message = "El número debe estar en formato E.164")
        @Schema(description = "Número E.164 con prefijo + y hasta 15 dígitos", example = "+5491112345678",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String numeroWhatsapp) {
}
