package com.bcra.insurance.ws.seguro.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.bcra.insurance.ws.seguro.dto.DenunciaResponse;
import com.bcra.insurance.ws.seguro.entity.Siniestro;

/** Proyecta el caso abierto hacia la respuesta de denuncia. */
@Mapper(componentModel = "spring")
public interface SiniestroMapper {

    @Mapping(target = "numeroCaso", source = "siniestro.numeroCaso")
    @Mapping(target = "proximosPasos", source = "proximosPasos")
    @Mapping(target = "plazos", source = "plazos")
    DenunciaResponse toDenunciaResponse(Siniestro siniestro, String proximosPasos, String plazos);
}
