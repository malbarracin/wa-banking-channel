package com.bcra.channel.ws.exception;

/**
 * Solicitud inválida con mensaje seguro para el cliente (HTTP 400 / {@link ErrorCode#BAD_REQUEST}).
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
