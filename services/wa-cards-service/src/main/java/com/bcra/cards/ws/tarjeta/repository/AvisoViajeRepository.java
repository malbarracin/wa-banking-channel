package com.bcra.cards.ws.tarjeta.repository;

import com.bcra.cards.ws.tarjeta.entity.AvisoViaje;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas de persistencia de AvisoViaje. */
public interface AvisoViajeRepository extends JpaRepository<AvisoViaje, UUID> {

    List<AvisoViaje> findByTarjetaIdOrderByFechaRegistroDesc(UUID tarjetaId);
}

