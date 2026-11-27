package com.bcra.channel.ws.binding.exception;

import com.bcra.channel.ws.exception.NotFoundException;

/**
 * Vínculo requerido no encontrado (cuando la operación exige registro previo).
 */
public class BindingNotFoundException extends NotFoundException {

    public BindingNotFoundException() {
        super("No existe vínculo para el número indicado");
    }
}
