package com.bcra.accounts.ws.cuenta.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.accounts.ws.cuenta.entity.LimiteCanal;

/** Acceso a persistencia de LimiteCanal. */
public interface LimiteCanalRepository extends JpaRepository<LimiteCanal, UUID> {

    Optional<LimiteCanal> findByProductoAndCanal(String producto, String canal);
}

