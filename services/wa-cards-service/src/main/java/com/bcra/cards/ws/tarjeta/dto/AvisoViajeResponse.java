package com.bcra.cards.ws.tarjeta.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/** Confirmación del aviso sin datos sensibles de la tarjeta. */
public record AvisoViajeResponse(
        @Schema(description = "Identificador de tarjeta; debe coincidir con el path",
                example = "11111111-1111-4111-8111-111111111111") UUID tarjetaId,
        @Schema(description = "País de destino", example = "España") String pais,
        @Schema(description = "Región de destino opcional", example = "Madrid") String region,
        @Schema(description = "Inicio del viaje, fecha ISO-8601", example = "2026-12-01") LocalDate fechaDesde,
        @Schema(description = "Fin del viaje, fecha ISO-8601 igual o posterior al inicio",
                example = "2026-12-15") LocalDate fechaHasta,
        @Schema(description = "Confirmación, recomendaciones y políticas aplicables",
                example = "Aviso de viaje registrado y confirmado para España / Madrid, "
                        + "desde 2026-12-01 hasta 2026-12-15. Usá canales oficiales y no "
                        + "compartas tus claves. Siguen aplicando las políticas "
                        + "internacionales del emisor y los límites del banco.") String mensajeConfirmacion) {
}
