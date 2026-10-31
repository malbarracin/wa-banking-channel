package com.bcra.channel.ws.vinculo.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.channel.ws.vinculo.entity.VinculoAuditLog;

/** Persistencia de eventos sin dependencia de la existencia del vínculo. */
public interface VinculoAuditLogRepository extends JpaRepository<VinculoAuditLog, UUID> {

    List<VinculoAuditLog> findByNumeroWhatsappOrderByFechaDesc(String numeroWhatsapp);
}
