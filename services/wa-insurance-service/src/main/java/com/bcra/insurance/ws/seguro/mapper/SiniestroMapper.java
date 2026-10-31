package com.bcra.insurance.ws.seguro.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.bcra.insurance.ws.seguro.dto.DenunciaResponse;
import com.bcra.insurance.ws.seguro.dto.SiniestroResponse;
import com.bcra.insurance.ws.seguro.entity.Siniestro;

/** Proyecta el caso persistido hacia respuestas de denuncia y seguimiento. */
@Mapper(componentModel = "spring")
public interface SiniestroMapper {

    @Mapping(target = "numeroCaso", source = "siniestro.numeroCaso")
    @Mapping(target = "proximosPasos", source = "proximosPasos")
    @Mapping(target = "plazos", source = "plazos")
    DenunciaResponse toDenunciaResponse(Siniestro siniestro, String proximosPasos, String plazos);

    @Mapping(target = "pendiente", source = ".", qualifiedByName = "pendiente")
    SiniestroResponse toResponse(Siniestro siniestro);

    @Named("pendiente")
    default String pendiente(Siniestro siniestro) {
        if (siniestro.getPendiente() != null && !siniestro.getPendiente().isBlank()) {
            return siniestro.getPendiente();
        }
        return switch (siniestro.getEstado()) {
            case RECIBIDO, EN_ANALISIS -> "Sin acciones pendientes del cliente";
            case DOCUMENTACION_PENDIENTE -> "Documentación pendiente";
            case RESUELTO -> "Ninguna";
        };
    }
}
