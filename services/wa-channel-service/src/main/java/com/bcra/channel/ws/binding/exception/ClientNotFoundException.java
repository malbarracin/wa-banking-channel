package com.bcra.channel.ws.binding.exception;

import com.bcra.channel.ws.exception.NotFoundException;

/**
 * Cliente inexistente en el registro del banco (H1).
 */
public class ClientNotFoundException extends NotFoundException {

    public ClientNotFoundException(String message) {
        super(message);
    }
}
