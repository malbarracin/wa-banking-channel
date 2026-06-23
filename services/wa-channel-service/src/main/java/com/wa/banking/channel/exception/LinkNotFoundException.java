package com.wa.banking.channel.exception;

/**
 * Excepción lanzada cuando no se encuentra un vínculo de canal.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public class LinkNotFoundException extends RuntimeException {

    public LinkNotFoundException(String id) {
        super("Channel link not found with id " + id);
    }
}
