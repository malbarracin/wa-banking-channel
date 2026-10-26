package com.bcra.credentials.ws.credencial.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;

/** Causa por la que una credencial deja de ser válida. */
@Schema(description =
        "BLOQUEO, DESVINCULACION, FRAUDE, POLITICA_BANCO, REVOCACION_MASIVA o "
        + "REEMPLAZO. H3 también puede enviar SOLICITUD_CLIENTE o "
        + "INTENTOS_AGOTADOS, ambos interpretados como BLOQUEO.")
public enum MotivoRevocacion {
    @JsonAlias({"SOLICITUD_CLIENTE", "INTENTOS_AGOTADOS"})
    BLOQUEO, DESVINCULACION, FRAUDE, POLITICA_BANCO, REVOCACION_MASIVA, REEMPLAZO
}

