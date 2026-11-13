package com.bcra.ws.users.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.bcra.ws.users.domain.DocumentType;
import com.bcra.ws.users.entity.UserEntity;

/**
 * Acceso a persistencia de usuarios. Soporta consulta por id y por documento único.
 *
 * @since 2026-10-19
 */
public interface UserRepository extends MongoRepository<UserEntity, String> {

    /** Comprueba unicidad independiente del email. */
    boolean existsByEmail(String email);

    /** Excluye al propio usuario al comprobar el email. */
    boolean existsByEmailAndIdNot(String email, String id);

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
