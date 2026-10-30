package com.bcra.cards.ws.tarjeta.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.bcra.cards.ws.tarjeta.entity.EstadoTarjeta;
import com.bcra.cards.ws.tarjeta.entity.MarcaTarjeta;
import com.bcra.cards.ws.tarjeta.entity.TipoTarjeta;
import java.util.UUID;

/** Datos de la tarjeta permitidos en el canal. */
public record TarjetaResponse(
        @Schema(description = "Identificador de la tarjeta", example = "11111111-1111-4111-8111-111111111111") UUID id,
        @Schema(description = "Últimos cuatro dígitos; nunca se expone el PAN", example = "1234") String ultimosCuatro,
        @Schema(description = "Marca de la tarjeta", example = "VISA") MarcaTarjeta marca,
        @Schema(description = "Tipo de tarjeta", example = "CREDITO") TipoTarjeta tipo,
        @Schema(description = "Estado actual de la tarjeta", example = "ACTIVA") EstadoTarjeta estado) {
}
