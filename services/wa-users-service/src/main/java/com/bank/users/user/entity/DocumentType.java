package com.bank.users.user.entity;

/**
 * Tipos de documento de identidad admitidos para identificar a un cliente.
 * Junto con el número de documento forman la identidad única del usuario.
 */
public enum DocumentType {
    /** Documento Nacional de Identidad. */
    DNI,
    /** Pasaporte. */
    PASAPORTE,
    /** Clave Única de Identificación Tributaria. */
    CUIT,
    /** Código Único de Identificación Laboral. */
    CUIL
}
