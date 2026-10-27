package com.bcra.accounts.ws.cuenta.dto;

/** Confirmación literal del cliente; el identificador se recibe en el path. */
public record ConfirmarTransferenciaRequest(String confirmacion) {
}
