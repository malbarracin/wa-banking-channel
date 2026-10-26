package com.bcra.users.ws.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.users.ws.user.entity.User;

/** Persistencia y consulta por documento, sin filtrar por estado operativo. */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByDocumentoTipoAndDocumentoNumero(String tipo, String numero);
}
