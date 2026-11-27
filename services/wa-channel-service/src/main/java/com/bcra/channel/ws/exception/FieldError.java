package com.bcra.channel.ws.exception;

/**
 * Detalle de un campo inválido en una respuesta de error.
 *
 * @param field   nombre del campo
 * @param message descripción del problema
 */
public record FieldError(String field, String message) {
}
