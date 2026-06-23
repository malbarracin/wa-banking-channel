package com.wa.banking.channel.exception;

/**
 * Excepción lanzada cuando la verificación está temporalmente bloqueada (RN5).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public class VerificationBlockedException extends RuntimeException {

    public VerificationBlockedException() {
        super("Verification temporarily blocked due to exceeded attempts");
    }
}
