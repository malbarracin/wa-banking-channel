package com.bank.users.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;

/**
 * Persistencia de usuarios. La unicidad de documento la garantiza la restricción
 * {@link User#UK_DOCUMENT} de la base de datos, también ante altas concurrentes.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Busca un usuario por su documento, sin importar su estado.
     *
     * @param documentType   tipo de documento
     * @param documentNumber número de documento
     * @return el usuario, o vacío si no existe
     */
    Optional<User> findByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);

    /**
     * Indica si existe un usuario con ese documento en cualquier estado.
     *
     * @param documentType   tipo de documento
     * @param documentNumber número de documento
     * @return {@code true} si el documento ya está registrado
     */
    boolean existsByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);
}
