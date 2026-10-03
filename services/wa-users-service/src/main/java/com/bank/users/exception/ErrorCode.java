package com.bank.users.exception;

/**
 * Códigos de error del contrato uniforme del Servicio de Usuarios.
 *
 * <p>Conjunto cerrado: los consumidores pueden distinguir el tipo de fallo sin parsear el mensaje.</p>
 */
public enum ErrorCode {

    /** Campos inválidos o faltantes (Bean Validation / parámetros obligatorios). HTTP 400. */
    VALIDATION_ERROR,

    /**
     * Solicitud mal formada: JSON inválido, valor de enum desconocido, campo no modificable
     * o transición de estado no permitida. HTTP 400 (u otro 4xx técnico de Spring MVC).
     */
    BAD_REQUEST,

    /** Usuario o recurso inexistente. HTTP 404. */
    NOT_FOUND,

    /** Conflicto con datos existentes (documento duplicado). HTTP 409. */
    CONFLICT,

    /** Fallo inesperado, sin detalles internos. HTTP 500. */
    INTERNAL_ERROR
}
