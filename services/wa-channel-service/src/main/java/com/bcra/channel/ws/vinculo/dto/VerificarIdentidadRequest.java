package com.bcra.channel.ws.vinculo.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Datos efímeros de identidad, consumidos exclusivamente por la estrategia de verificación.
 * El método concreto de verificación queda pendiente de Compliance; por eso las pruebas viajan como mapa.
 */
@Schema(description = "Identidad del solicitante y pruebas efímeras; no se persisten ni se registran")
public record VerificarIdentidadRequest(
        @NotBlank @Size(max = 255)
        @Schema(description = "Tipo de documento del cliente en H1", example = "DNI",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String documentoTipo,
        @NotBlank @Size(max = 255)
        @Schema(description = "Número de documento del cliente en H1", example = "30111222",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String documentoNumero,
        @NotNull @Size(max = 32)
        @Schema(description = "Pruebas según el método aprobado por Compliance (máximo 32 claves). "
                + "El servicio por defecto rechaza toda prueba hasta instalar la estrategia.",
                example = "{\"codigo\":\"123456\"}", requiredMode = Schema.RequiredMode.REQUIRED)
        Map<@NotBlank @Size(max = 100) String,
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
