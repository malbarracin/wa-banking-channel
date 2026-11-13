package com.ingenia.wa.users.api.error;

import java.util.List;
import java.util.Map;

import com.ingenia.wa.users.domain.DocumentType;

/**
 * Conflicto de unicidad: ya existe un usuario con el mismo documento.
 * Se mapea a HTTP 409 con {@code code=BAD_REQUEST} según contrato del plan.
 *
 * @author Ingenia
 * @since 2026-10-19
 * Contacto: marcelo.albarracin@ingenia.la
 */
public class DuplicateDocumentException extends RuntimeException {

    private final DocumentType documentType;
    private final String documentNumber;

    public DuplicateDocumentException(DocumentType documentType, String documentNumber) {
        super("A user with the same document already exists");
        this.documentType = documentType;
        this.documentNumber = documentNumber;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    /**
     * Detalles para el error contract.
     *
     * @return lista de detalles de campo
     */
    public List<Object> getDetails() {
        return List.of(Map.of(
                "field", "documentNumber",
                "message", "Document already registered: " + documentType + "/" + documentNumber));
    }
}
