package com.bcra.credentials.ws.credencial.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.credentials.ws.credencial.entity.EntregaRenovacion;

/** Una única entrega por credencial anterior, compartida entre instancias de H2. */
public interface EntregaRenovacionRepository extends JpaRepository<EntregaRenovacion, UUID> {

    Optional<EntregaRenovacion> findByJti(String jti);
}
