package com.bank.users.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;

/** Operaciones de persistencia de usuarios; incluye búsqueda por clave documental. */
public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);

    Optional<User> findByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);
}
