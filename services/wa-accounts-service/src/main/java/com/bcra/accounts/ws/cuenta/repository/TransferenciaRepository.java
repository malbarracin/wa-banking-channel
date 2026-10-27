package com.bcra.accounts.ws.cuenta.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.accounts.ws.cuenta.entity.Transferencia;
import com.bcra.accounts.ws.cuenta.entity.EstadoTransferencia;

/** Acceso a persistencia de Transferencia. */
public interface TransferenciaRepository extends JpaRepository<Transferencia, UUID> {

    Optional<Transferencia> findByIdAndEstado(UUID id, EstadoTransferencia estado);

    List<Transferencia> findByCuentaOrigenIdAndFechaCreacionAfter(UUID cuentaOrigenId, Instant desde);
}

