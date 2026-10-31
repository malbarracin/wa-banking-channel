package com.bcra.accounts.ws.cuenta.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bcra.accounts.ws.cuenta.entity.Transferencia;
import com.bcra.accounts.ws.cuenta.entity.EstadoTransferencia;

/** Acceso a persistencia de Transferencia. */
public interface TransferenciaRepository extends JpaRepository<Transferencia, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transferencia t where t.id = :id "
            + "and t.cuentaOrigen.titularDocumentoTipo = :tipo "
            + "and t.cuentaOrigen.titularDocumentoNumero = :numero")
    Optional<Transferencia> bloquearDelTitular(@Param("id") UUID id, @Param("tipo") String tipo,
            @Param("numero") String numero);

    @Query("select t from Transferencia t join fetch t.cuentaOrigen c "
            + "where c.titularDocumentoTipo = :tipo and c.titularDocumentoNumero = :numero "
            + "and (coalesce(t.fechaEjecucion, t.fechaCreacion) >= :desde or t.estado in "
            + "(com.bcra.accounts.ws.cuenta.entity.EstadoTransferencia.PREPARADA, "
            + "com.bcra.accounts.ws.cuenta.entity.EstadoTransferencia.CONFIRMADA))")
    List<Transferencia> buscarParaLimites(@Param("tipo") String tipo, @Param("numero") String numero,
            @Param("desde") Instant desde);

    Optional<Transferencia> findByIdAndEstado(UUID id, EstadoTransferencia estado);

    List<Transferencia> findByCuentaOrigenIdAndFechaCreacionAfter(UUID cuentaOrigenId, Instant desde);
}

