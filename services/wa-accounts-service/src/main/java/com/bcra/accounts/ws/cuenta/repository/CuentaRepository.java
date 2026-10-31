package com.bcra.accounts.ws.cuenta.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.bcra.accounts.ws.cuenta.entity.Cuenta;

/** Acceso a persistencia de Cuenta. */
public interface CuentaRepository extends JpaRepository<Cuenta, UUID> {

    /** Incluye destino de tercero local y ordena todos los bloqueos para evitar ciclos entre titulares. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cuenta c where (c.titularDocumentoTipo = :tipo "
            + "and c.titularDocumentoNumero = :numero) or c.numeroCuenta in "
            + "(select t.tercero.numeroCuentaDestino from Transferencia t where t.id = :id) order by c.id")
    List<Cuenta> bloquearParaConfirmar(@Param("tipo") String tipo, @Param("numero") String numero,
            @Param("id") UUID id);

    /** Serializa preparaciones del titular incluso si parten de cuentas distintas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cuenta c where c.titularDocumentoTipo = :tipo "
            + "and c.titularDocumentoNumero = :numero order by c.id")
    List<Cuenta> bloquearDelTitular(@Param("tipo") String tipo, @Param("numero") String numero);

    List<Cuenta> findByTitularDocumentoTipoAndTitularDocumentoNumero(String tipo, String numero);

    Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);

    Optional<Cuenta> findByNumeroCuentaAndTitularDocumentoTipoAndTitularDocumentoNumero(
            String numeroCuenta, String tipo, String numero);
}

