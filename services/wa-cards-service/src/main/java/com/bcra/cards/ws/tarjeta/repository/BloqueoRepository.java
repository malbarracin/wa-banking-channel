package com.bcra.cards.ws.tarjeta.repository;

import com.bcra.cards.ws.tarjeta.entity.Bloqueo;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas de persistencia de Bloqueo. */
public interface BloqueoRepository extends JpaRepository<Bloqueo, UUID> {

    List<Bloqueo> findByTarjetaIdOrderByFechaBloqueoDesc(UUID tarjetaId);
}

