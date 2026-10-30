package com.bcra.cards.ws.tarjeta.dto;

/** Resultado del bloqueo definitivo y orientación para solicitar reposición. */
public record BloqueoDefinitivoResponse(TarjetaResponse tarjeta, String mensaje) {
}
