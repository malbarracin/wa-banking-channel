package com.wa.banking.channel.exception;

import com.wa.banking.channel.entity.LinkStatus;

/**
 * Excepción lanzada cuando la operación no es válida para el estado actual del vínculo.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public class InvalidLinkStateException extends RuntimeException {

    public InvalidLinkStateException(LinkStatus current, String operation) {
        super("Cannot perform " + operation + " when link status is " + current);
    }
}
