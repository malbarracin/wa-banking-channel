package com.bcra.channel.ws.binding.exception;

import com.bcra.channel.ws.exception.NotFoundException;

/**
 * Vínculo requerido no encontrado (cuando la operación exige registro previo).
 */
public class BindingNotFoundException extends NotFoundException {

    public BindingNotFoundException() {
        this("No existe vínculo para el número indicado");
    }

    /**
     * @param message mensaje sin datos sensibles
     */
    public BindingNotFoundException(String message) {
        super(message);
    }
}
