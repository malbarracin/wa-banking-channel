package com.wa.banking.users.exception;

/**
 * Excepción de dominio cuando ya existe un usuario con el mismo documento.
 *
 * @author licius-it
 * @since 2026-06-22
 * @contact marceloalejandro.albarracin@gmail.com
 */
public class DuplicateDocumentException extends RuntimeException {

    public DuplicateDocumentException(String message) {
        super(message);
    }
}
