package com.bcra.credentials.ws.credencial.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Origen de la decisión de revocación. */
@Schema(description = "CANAL: pedido de H3; BANCO: política o riesgo; SISTEMA: proceso interno.")
public enum RevocadaPor {
    CANAL, BANCO, SISTEMA
}

