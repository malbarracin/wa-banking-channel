package com.bcra.accounts.ws.cuenta.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.accounts.ws.cuenta.entity.AuditoriaCuenta;

/** Acceso a persistencia de AuditoriaCuenta. */
public interface AuditoriaCuentaRepository extends JpaRepository<AuditoriaCuenta, UUID> {

    Page<AuditoriaCuenta> findByNumeroWhatsappOrderByFechaDesc(String numeroWhatsapp, Pageable pageable);
}

