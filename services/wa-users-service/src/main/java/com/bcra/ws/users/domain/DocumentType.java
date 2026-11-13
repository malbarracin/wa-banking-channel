package com.bcra.ws.users.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tipos de documento de identidad admitidos en el alta de usuarios.
 */
@Schema(description = "Tipo de documento de identidad (parte de la clave de unicidad)")
public enum DocumentType {

    DNI,
    PASSPORT,
    CUIT
}
