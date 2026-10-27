package com.bcra.insurance.ws.seguro.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.insurance.ws.seguro.entity.Siniestro;
import com.bcra.insurance.ws.seguro.entity.EstadoSiniestro;
import com.bcra.insurance.ws.seguro.entity.TipoSiniestro;

/** Consultas persistentes de siniestro. */
public interface SiniestroRepository extends JpaRepository<Siniestro, UUID> {

    Optional<Siniestro> findByNumeroCaso(String numeroCaso);

    List<Siniestro> findByPolizaIdAndTipoSiniestroAndEstadoInAndFechaAperturaAfter(
            UUID polizaId, TipoSiniestro tipoSiniestro, Collection<EstadoSiniestro> estados, Instant fechaApertura);
}
