package com.bcra.insurance.ws.seguro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.insurance.ws.seguro.entity.Cobertura;

/** Consultas persistentes de cobertura. */
public interface CoberturaRepository extends JpaRepository<Cobertura, UUID> {

    List<Cobertura> findByPolizaId(UUID polizaId);
}
