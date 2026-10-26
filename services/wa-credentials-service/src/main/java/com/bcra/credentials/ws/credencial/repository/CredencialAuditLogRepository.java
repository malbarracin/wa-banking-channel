package com.bcra.credentials.ws.credencial.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.credentials.ws.credencial.entity.CredencialAuditLog;

/** Historial cronológico de eventos de credenciales. */
public interface CredencialAuditLogRepository extends JpaRepository<CredencialAuditLog, UUID> {

    List<CredencialAuditLog> findByNumeroWhatsappOrderByFechaDesc(String numeroWhatsapp);
}

