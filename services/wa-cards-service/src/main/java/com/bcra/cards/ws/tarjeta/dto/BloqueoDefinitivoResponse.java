package com.bcra.cards.ws.tarjeta.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resultado del bloqueo definitivo y orientación para solicitar reposición. */
public record BloqueoDefinitivoResponse(
        @Schema(description = "Tarjeta con su estado actualizado") TarjetaResponse tarjeta,
        @Schema(description = "Orientación al cliente",
                example = "Bloqueo definitivo aplicado, irreversible por WhatsApp. Solicite "
                        + "la reposición por los canales oficiales del banco.") String mensaje) {
}
