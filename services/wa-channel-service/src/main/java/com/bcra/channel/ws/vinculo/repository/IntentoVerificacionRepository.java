package com.bcra.channel.ws.vinculo.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.channel.ws.vinculo.entity.IntentoVerificacion;
import com.bcra.channel.ws.vinculo.entity.ResultadoIntento;

/** Cuenta intentos por vínculo, resultado y límite inferior exclusivo de la ventana. */
public interface IntentoVerificacionRepository extends JpaRepository<IntentoVerificacion, UUID> {

    long countByVinculoIdAndResultadoAndFechaAfter(UUID vinculoId, ResultadoIntento resultado, Instant fecha);
}

