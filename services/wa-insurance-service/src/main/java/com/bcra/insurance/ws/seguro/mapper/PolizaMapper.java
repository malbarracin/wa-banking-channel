package com.bcra.insurance.ws.seguro.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.bcra.insurance.ws.seguro.dto.PolizaResponse;
import com.bcra.insurance.ws.seguro.entity.Poliza;

/** Proyección de pólizas sin documentos personales. */
@Mapper(componentModel = "spring")
public interface PolizaMapper {

    int LONGITUD_IDENTIFICADOR = 4;

    @Mapping(target = "identificadorCorto", source = "numeroPoliza", qualifiedByName = "identificadorCorto")
    PolizaResponse toResponse(Poliza poliza);

    @Named("identificadorCorto")
    default String identificadorCorto(String numero) {
        return numero.substring(Math.max(0, numero.length() - LONGITUD_IDENTIFICADOR));
    }
}
