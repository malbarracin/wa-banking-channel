package com.bcra.users.ws.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.users.ws.user.entity.DocumentType;
import com.bcra.users.ws.user.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByTipoDocumentoAndNumeroDocumento(DocumentType tipoDocumento, String numeroDocumento);

    boolean existsByTipoDocumentoAndNumeroDocumento(DocumentType tipoDocumento, String numeroDocumento);
}
