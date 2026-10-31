package com.bcra.users.ws.user.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;

import com.bcra.users.ws.user.exception.NonEditableFieldException;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cambios permitidos. Los campos ausentes o null conservan el valor; preferencias reemplaza el texto completo. */
@Schema(description = "Solo campos editables: email, telefono, nombreTratamiento, preferencias")
public record UpdateUserRequest(
        @Schema(description = "Nuevo email; null omite el cambio", example = "nuevo@example.test") String email,
        @Schema(description = "Nuevo teléfono; null omite el cambio", example = "555999") String telefono,
        @Schema(description = "Nombre para tratamiento", example = "Anita") String nombreTratamiento,
        @Schema(description = "Preferencias generales no sensibles (texto completo)",
                example = "canal:whatsapp") String preferencias) {

    /** Rechaza cualquier propiedad fuera del contrato, incluso si Jackson ignora desconocidas globalmente. */
    @JsonAnySetter
    public void rechazarCampo(String campo, JsonNode valor) {
        throw new NonEditableFieldException();
    }
}
