package com.bcra.cards.ws.tarjeta.dto;

import com.bcra.cards.ws.tarjeta.entity.EstadoTarjeta;
import com.bcra.cards.ws.tarjeta.entity.MarcaTarjeta;
import com.bcra.cards.ws.tarjeta.entity.TipoTarjeta;
import java.util.UUID;

/** Datos de la tarjeta permitidos en el canal. */
public record TarjetaResponse(UUID id, String ultimosCuatro, MarcaTarjeta marca,
        TipoTarjeta tipo, EstadoTarjeta estado) {
}
