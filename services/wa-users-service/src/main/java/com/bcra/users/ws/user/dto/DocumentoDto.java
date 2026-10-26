package com.bcra.users.ws.user.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** Documento utilizado como clave de unicidad, sin catálogo ni formato cerrado. */
@Schema(description = "Documento identificador; el catálogo de tipos queda abierto en H1")
public record DocumentoDto(
        @NotBlank(message = "El tipo de documento es obligatorio")
        @Schema(description = "Tipo libre (ej. DNI, PASAPORTE)", example = "DNI",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String tipo,
        @NotBlank(message = "El número de documento es obligatorio")
        @Schema(description = "Número del documento", example = "30111222",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String numero) {
}
