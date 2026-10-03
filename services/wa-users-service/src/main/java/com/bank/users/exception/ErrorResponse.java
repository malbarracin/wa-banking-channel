package com.bank.users.exception;

import java.time.Instant;
import java.util.List;

/**
 * Cuerpo uniforme de error: solo {@code code}, {@code message}, {@code details} y {@code timestamp}.
 *
 * <p>{@code details} siempre es un array (vacío si no aplica) y {@code timestamp} es ISO-8601 (UTC).
 * Nunca incluye stacktraces, credenciales ni valores enviados por el cliente.</p>
 *
 * @param code      código de error del contrato
 * @param message   mensaje legible, sin datos sensibles
 * @param details   detalle por campo (puede estar vacío, nunca {@code null})
 * @param timestamp instante del error en ISO-8601
 */
public record ErrorResponse(ErrorCode code, String message, List<Detail> details, String timestamp) {

    /**
     * Normaliza {@code details} a una lista inmutable no nula.
     */
    public ErrorResponse {
        details = details == null ? List.of() : List.copyOf(details);
    }

    /**
     * Crea un error con timestamp actual y sin detalles.
     *
     * @param code    código de error
     * @param message mensaje legible
     * @return respuesta de error
     */
    public static ErrorResponse of(ErrorCode code, String message) {
        return of(code, message, List.of());
    }

    /**
     * Crea un error con timestamp actual.
     *
     * @param code    código de error
     * @param message mensaje legible
     * @param details detalle por campo
     * @return respuesta de error
     */
    public static ErrorResponse of(ErrorCode code, String message, List<Detail> details) {
        return new ErrorResponse(code, message, details, Instant.now().toString());
    }

    /**
     * Detalle de un campo inválido, faltante o no modificable.
     *
     * @param field   nombre del campo o parámetro (ruta JSON si es anidado)
     * @param message motivo, sin el valor enviado
     */
    public record Detail(String field, String message) {
    }
}
