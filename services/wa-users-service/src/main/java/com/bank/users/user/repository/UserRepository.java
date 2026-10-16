package com.bank.users.user.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bank.users.user.entity.User;

/** Persistencia y comprobación de unicidad del documento bancario. */
public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByTipoDocumentoAndNumeroDocumento(String tipoDocumento, String numeroDocumento);
}
