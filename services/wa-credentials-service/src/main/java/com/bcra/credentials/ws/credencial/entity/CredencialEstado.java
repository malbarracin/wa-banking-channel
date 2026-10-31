package com.bcra.credentials.ws.credencial.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Estado persistido de la credencial. */
@Schema(description = "ACTIVA: vigente; REVOCADA: invalidada; EXPIRADA: alcanzó su vencimiento.")
public enum CredencialEstado {
    ACTIVA, REVOCADA, EXPIRADA
}

