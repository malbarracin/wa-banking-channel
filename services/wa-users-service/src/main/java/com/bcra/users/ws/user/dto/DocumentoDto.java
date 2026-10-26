package com.bcra.users.ws.user.dto;

import jakarta.validation.constraints.NotBlank;

/** Documento utilizado como clave de unicidad, sin catálogo ni formato cerrado. */
public record DocumentoDto(
        @NotBlank(message = "El tipo de documento es obligatorio") String tipo,
        @NotBlank(message = "El número de documento es obligatorio") String numero) {
}
