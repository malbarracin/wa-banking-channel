package com.bcra.credentials.ws.credencial.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cantidad de credenciales que cambiaron de ACTIVA a REVOCADA en esta operación. */
@Schema(description = "Cantidad de credenciales que cambiaron de ACTIVA a REVOCADA en esta operación.")
public record RevocacionMasivaResponse(
        @Schema(description = "Cantidad que cambió de ACTIVA a REVOCADA; cero si no hay coincidencias",
                example = "1")
        int revocadas) {
}
