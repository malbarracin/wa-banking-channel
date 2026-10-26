package com.bcra.channel.ws.vinculo.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Datos efímeros de identidad, consumidos exclusivamente por la estrategia de verificación. */
public record VerificarIdentidadRequest(
        @NotBlank @Size(max = 255) String documentoTipo,
        @NotBlank @Size(max = 255) String documentoNumero,
        @NotNull @Size(max = 32) Map<@NotBlank @Size(max = 100) String,
                @NotBlank @Size(max = 2048) String> datosVerificacion) {

    public VerificarIdentidadRequest {
        if (datosVerificacion != null) {
            datosVerificacion = Map.copyOf(datosVerificacion);
        }
    }

    @Override
    public String toString() {
        return "VerificarIdentidadRequest[datos protegidos]";
    }
}
