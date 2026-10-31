package com.bcra.channel.ws.vinculo.dto;

import java.time.Instant;
import java.util.UUID;

import com.bcra.channel.ws.vinculo.entity.VinculoEstado;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resultado del proceso para H0.2; no constituye una credencial de sesión de H2. */
@Schema(description = "Resultado verificable de identidad y vínculo para H0.2; no es una credencial de H2")
public record VerificacionResultadoResponse(
        @Schema(description = "true si la identidad fue validada y el vínculo quedó ACTIVO", example = "true")
        boolean exitosa,
        @Schema(description = "Identificador del vínculo involucrado",
                example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        UUID vinculoId,
        @Schema(description = "Número E.164 verificado", example = "+5491112345678")
        String numeroWhatsapp,
        @Schema(description = "Tipo de documento informado", example = "DNI")
        String documentoTipo,
        @Schema(description = "Número de documento informado", example = "30111222")
        String documentoNumero,
        @Schema(description = "Estado del vínculo tras el intento: ACTIVO si exitosa, "
                + "PENDIENTE_VERIFICACION si falló", example = "ACTIVO")
        VinculoEstado estadoVinculo,
        @Schema(description = "Intentos disponibles tras un fallo; null si la verificación fue exitosa",
                example = "2")
        Integer intentosRestantes,
        @Schema(description = "Instante UTC de la verificación exitosa; null si falló",
                example = "2026-10-26T12:00:00Z")
        Instant verificadoEn) {

    @Override
    public String toString() {
        return "VerificacionResultadoResponse[exitosa=" + exitosa + "]";
    }
}
