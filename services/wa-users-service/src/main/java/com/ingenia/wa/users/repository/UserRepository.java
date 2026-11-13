package com.ingenia.wa.users.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.ingenia.wa.users.domain.DocumentType;
import com.ingenia.wa.users.entity.UserEntity;

/**
 * Acceso a persistencia de usuarios. Soporta consulta por id y por documento único.
 *
 * @author Ingenia
 * @since 2026-10-19
 * Contacto: marcelo.albarracin@ingenia.la
 */
public interface UserRepository extends MongoRepository<UserEntity, String> {

    /**
     * Indica si ya existe un usuario con el par documento indicado.
     *
     * @param documentType   tipo de documento
     * @param documentNumber número de documento
     * @return true si existe
     */
    boolean existsByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);

    /**
     * Busca un usuario por tipo y número de documento.
     *
     * @param documentType   tipo de documento
     * @param documentNumber número de documento
     * @return usuario si existe
     */
    Optional<UserEntity> findByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);
}
