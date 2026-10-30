package com.bcra.cards.ws.tarjeta.repository;

import com.bcra.cards.ws.tarjeta.entity.Consumo;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas de persistencia de Consumo. */
public interface ConsumoRepository extends JpaRepository<Consumo, UUID> {

    Page<Consumo> findByTarjetaIdAndFechaAfterOrderByFechaDesc(UUID tarjetaId, Instant fecha, Pageable pageable);

    Page<Consumo> findByTarjetaIdAndFechaBetweenOrderByFechaDesc(
            UUID tarjetaId, Instant desde, Instant hasta, Pageable pageable);
}

