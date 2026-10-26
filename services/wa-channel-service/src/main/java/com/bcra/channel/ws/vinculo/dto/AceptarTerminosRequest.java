package com.bcra.channel.ws.vinculo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Aceptación explícita de términos; no requiere datos de identificación del cliente. */
public record AceptarTerminosRequest(
        @NotBlank(message = "El número de WhatsApp es obligatorio")
        @Pattern(regexp = "^\\+[1-9][0-9]{1,14}$", message = "El número debe estar en formato E.164")
        String numeroWhatsapp) {
}
