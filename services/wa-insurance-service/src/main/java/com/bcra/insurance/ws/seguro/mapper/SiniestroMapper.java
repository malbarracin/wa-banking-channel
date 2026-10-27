package com.bcra.insurance.ws.seguro.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.bcra.insurance.ws.seguro.dto.SiniestroResponse;
import com.bcra.insurance.ws.seguro.entity.Siniestro;

/** Proyecta el caso persistido hacia la respuesta de seguimiento. */
@Mapper(componentModel = "spring")
public interface SiniestroMapper {

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
