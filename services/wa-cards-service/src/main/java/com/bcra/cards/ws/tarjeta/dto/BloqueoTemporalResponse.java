package com.bcra.cards.ws.tarjeta.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.bcra.cards.ws.tarjeta.entity.EstadoTarjeta;
import java.util.UUID;

/** Resultado del bloqueo y orientación para revertirlo. */
public record BloqueoTemporalResponse(
        @Schema(description = "Identificador de tarjeta; debe coincidir con el path",
                example = "11111111-1111-4111-8111-111111111111") UUID tarjetaId,
        @Schema(description = "Estado actual de la tarjeta", example = "BLOQUEO_TEMPORAL") EstadoTarjeta estado,
        @Schema(description = "Orientación al cliente",
                example = "Tarjeta bloqueada temporalmente. Para desbloquearla, indicá DESBLOQUEAR.") String mensaje) {
}
