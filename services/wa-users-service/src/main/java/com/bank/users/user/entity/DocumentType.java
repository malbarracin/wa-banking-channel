package com.bank.users.user.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Catálogo de documentos admitidos en el alta. */
@Schema(description = "Tipo de documento de identidad admitido en el alta del usuario",
        allowableValues = {"DNI", "PASSPORT", "LC", "LE", "CUIT"})
public enum DocumentType {
    /** Documento Nacional de Identidad. */
    @Schema(description = "Documento Nacional de Identidad")
    DNI,
    /** Pasaporte. */
    @Schema(description = "Pasaporte")
    PASSPORT,
    /** Libreta de Cámara (Argentina). */
    @Schema(description = "Libreta de Cámara (Argentina)")
    LC,
    /** Libreta de Enrolamiento (Argentina). */
    @Schema(description = "Libreta de Enrolamiento (Argentina)")
    LE,
    /** Clave Única Tributaria. */
    @Schema(description = "Clave Única Tributaria")
    CUIT
}
