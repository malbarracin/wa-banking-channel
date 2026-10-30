package com.bcra.cards.ws.tarjeta.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/** Datos públicos de un consumo, sin información sensible de la tarjeta. */
public record ConsumoResponse(
        @Schema(description = "Instante UTC ISO-8601", example = "2026-10-30T12:00:00Z") Instant fecha,
        @Schema(description = "Nombre del comercio", example = "Comercio de prueba") String comercio,
        @Schema(description = "Importe del consumo", example = "1250.5") BigDecimal monto,
        @Schema(description = "Código de moneda", example = "ARS") String moneda,
        @Schema(description = "Descripción del consumo", example = "Compra") String descripcion,
        @Schema(description = "Referencia del consumo", example = "COMPRA-001") String referencia) {
}
