package com.bcra.credentials.ws.credencial.dto;

/** Cantidad de credenciales que cambiaron de ACTIVA a REVOCADA en esta operación. */
public record RevocacionMasivaResponse(int revocadas) {
}
