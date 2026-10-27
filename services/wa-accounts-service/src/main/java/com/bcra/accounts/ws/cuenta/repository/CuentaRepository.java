package com.bcra.accounts.ws.cuenta.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.accounts.ws.cuenta.entity.Cuenta;

/** Acceso a persistencia de Cuenta. */
public interface CuentaRepository extends JpaRepository<Cuenta, UUID> {

    List<Cuenta> findByTitularDocumentoTipoAndTitularDocumentoNumero(String tipo, String numero);

    Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);

    Optional<Cuenta> findByNumeroCuentaAndTitularDocumentoTipoAndTitularDocumentoNumero(
            String numeroCuenta, String tipo, String numero);
}

