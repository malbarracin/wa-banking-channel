package com.bcra.insurance.ws.seguro.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bcra.insurance.ws.seguro.entity.Poliza;

/** Consultas persistentes de poliza. */
public interface PolizaRepository extends JpaRepository<Poliza, UUID> {

    @Query("""
            select p from Poliza p where p.vigente = true
            and p.fechaVigenciaDesde <= :fecha and p.fechaVigenciaHasta >= :fecha
            and ((p.titularDocumentoTipo = :tipo and p.titularDocumentoNumero = :numero)
            or (p.aseguradoDocumentoTipo = :tipo and p.aseguradoDocumentoNumero = :numero))
            order by p.numeroPoliza
            """)
    List<Poliza> buscarVigentesAutorizadas(String tipo, String numero, LocalDate fecha);

    List<Poliza> findByTitularDocumentoTipoAndTitularDocumentoNumeroAndVigenteTrue(
            String titularDocumentoTipo, String titularDocumentoNumero);

    Optional<Poliza> findByIdAndTitularDocumentoTipoAndTitularDocumentoNumero(
            UUID id, String titularDocumentoTipo, String titularDocumentoNumero);
}
