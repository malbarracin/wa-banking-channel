package com.bcra.cards.ws.tarjeta.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Valores persistidos de MarcaTarjeta. */
@Schema(description = "Marca de la tarjeta")
public enum MarcaTarjeta {
    VISA,
    MASTERCARD,
    AMEX
}

