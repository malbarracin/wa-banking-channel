package com.bcra.cards.ws.tarjeta.repository;

import com.bcra.cards.ws.tarjeta.entity.AuditoriaTarjeta;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas de persistencia de AuditoriaTarjeta. */
public interface AuditoriaTarjetaRepository extends JpaRepository<AuditoriaTarjeta, UUID> {

    List<AuditoriaTarjeta> findByNumeroWhatsappOrderByFechaDesc(String numeroWhatsapp);
}

