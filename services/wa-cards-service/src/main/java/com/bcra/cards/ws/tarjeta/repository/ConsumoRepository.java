package com.bcra.cards.ws.tarjeta.repository;

import com.bcra.cards.ws.tarjeta.entity.Consumo;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Consultas de persistencia de Consumo. */
public interface ConsumoRepository extends JpaRepository<Consumo, UUID> {

    @Query("""
            select c from Consumo c where c.tarjetaId = :tarjetaId
            and c.fecha between :desde and :hasta
            and (c.fecha < :ultimaFecha or (c.fecha = :ultimaFecha and c.id < :ultimoId))
            order by c.fecha desc, c.id desc
            """)
    List<Consumo> buscarSiguientes(@Param("tarjetaId") UUID tarjetaId, @Param("desde") Instant desde,
            @Param("hasta") Instant hasta, @Param("ultimaFecha") Instant ultimaFecha,
            @Param("ultimoId") UUID ultimoId, Pageable pageable);

    Page<Consumo> findByTarjetaIdAndFechaAfterOrderByFechaDesc(UUID tarjetaId, Instant fecha, Pageable pageable);

    Page<Consumo> findByTarjetaIdAndFechaBetweenOrderByFechaDesc(
            UUID tarjetaId, Instant desde, Instant hasta, Pageable pageable);
}

