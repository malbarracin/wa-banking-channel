package com.bcra.channel.ws.vinculo.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import com.bcra.channel.ws.vinculo.dto.VinculoResponse;
import com.bcra.channel.ws.vinculo.entity.Vinculo;

/** Proyecta únicamente los datos públicos del vínculo. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface VinculoMapper {

    VinculoResponse toResponse(Vinculo vinculo);
}
