package com.wa.banking.channel.exception;

/**
 * Excepción lanzada cuando el usuario H1 no puede vincular canal (RN2).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public class UserCannotLinkException extends RuntimeException {

    public UserCannotLinkException(String reason) {
        super(reason);
    }
}
