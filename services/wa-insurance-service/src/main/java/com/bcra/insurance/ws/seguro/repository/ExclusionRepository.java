package com.bcra.insurance.ws.seguro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.insurance.ws.seguro.entity.Exclusion;

/** Consultas persistentes de exclusion. */
public interface ExclusionRepository extends JpaRepository<Exclusion, UUID> {

    List<Exclusion> findByPolizaId(UUID polizaId);
}
