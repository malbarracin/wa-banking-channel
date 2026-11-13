package com.bcra.ws.users.exception;

import java.util.List;

import com.bcra.ws.users.domain.DocumentType;

/** Conflicto por documento duplicado, sin retener datos personales. */
public class DuplicateDocumentException extends ConflictException {

    public DuplicateDocumentException(DocumentType documentType, String documentNumber) {
        super("A user with the same document already exists",
                List.of(new FieldError("documentNumber", "Document already registered")));
    }
}
