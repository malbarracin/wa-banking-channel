package com.bcra.channel.ws.vinculo.exception;

/** Propiedad del body fuera del contrato; se traduce a 400 VALIDATION_ERROR. */
public class PropiedadDesconocidaException extends RuntimeException {

    public PropiedadDesconocidaException() {
        super("La solicitud tiene propiedades no permitidas");
    }
}
