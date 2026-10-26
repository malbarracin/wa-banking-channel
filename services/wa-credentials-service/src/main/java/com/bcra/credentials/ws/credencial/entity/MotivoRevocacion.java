package com.bcra.credentials.ws.credencial.entity;

import com.fasterxml.jackson.annotation.JsonAlias;

/** Causa por la que una credencial deja de ser válida. */
public enum MotivoRevocacion {
    @JsonAlias({"SOLICITUD_CLIENTE", "INTENTOS_AGOTADOS"})
    BLOQUEO, DESVINCULACION, FRAUDE, POLITICA_BANCO, REVOCACION_MASIVA, REEMPLAZO
}

