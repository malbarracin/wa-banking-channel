package com.bcra.accounts.ws.cuenta.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.accounts.ws.cuenta.entity.Movimiento;
import com.bcra.accounts.ws.cuenta.entity.TipoMovimiento;

/** Acceso a persistencia de Movimiento. */
public interface MovimientoRepository extends JpaRepository<Movimiento, UUID> {

    Page<Movimiento> findByCuentaIdOrderByFechaDesc(UUID cuentaId, Pageable pageable);

    Page<Movimiento> findByCuentaIdAndFechaBetweenAndTipo(
            UUID cuentaId, Instant desde, Instant hasta, TipoMovimiento tipo, Pageable pageable);
}

