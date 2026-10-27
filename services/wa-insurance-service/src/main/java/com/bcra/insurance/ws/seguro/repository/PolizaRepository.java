package com.bcra.insurance.ws.seguro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.insurance.ws.seguro.entity.Poliza;

/** Consultas persistentes de poliza. */
public interface PolizaRepository extends JpaRepository<Poliza, UUID> {

    List<Poliza> findByTitularDocumentoTipoAndTitularDocumentoNumeroAndVigenteTrue(
            String titularDocumentoTipo, String titularDocumentoNumero);

    Optional<Poliza> findByIdAndTitularDocumentoTipoAndTitularDocumentoNumero(
            UUID id, String titularDocumentoTipo, String titularDocumentoNumero);
}
