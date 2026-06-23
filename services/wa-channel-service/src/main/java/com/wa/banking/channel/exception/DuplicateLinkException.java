package com.wa.banking.channel.exception;

/**
 * Excepción lanzada cuando ya existe un vínculo activo para el número (RN1).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public class DuplicateLinkException extends RuntimeException {

    public DuplicateLinkException(String phoneNumber) {
        super("Phone number " + phoneNumber + " is already linked to an active client");
    }
}
