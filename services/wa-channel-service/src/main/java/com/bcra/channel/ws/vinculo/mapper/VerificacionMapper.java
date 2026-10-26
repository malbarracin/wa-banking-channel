package com.bcra.channel.ws.vinculo.mapper;

import java.time.Instant;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.bcra.channel.ws.vinculo.dto.VerificacionResultadoResponse;
import com.bcra.channel.ws.vinculo.entity.Vinculo;

/** Proyecta el resultado de la verificación sin incluir sus pruebas. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface VerificacionMapper {

    @Mapping(target = "vinculoId", source = "vinculo.id")
    @Mapping(target = "estadoVinculo", source = "vinculo.estado")
    VerificacionResultadoResponse toResponse(Vinculo vinculo, boolean exitosa, Integer intentosRestantes,
            Instant verificadoEn);
}
