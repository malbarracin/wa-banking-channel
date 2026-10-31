package com.bcra.cards.ws.tarjeta.mapper;

import com.bcra.cards.ws.tarjeta.dto.AvisoViajeRequest;
import com.bcra.cards.ws.tarjeta.dto.AvisoViajeResponse;
import com.bcra.cards.ws.tarjeta.entity.AvisoViaje;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Mapea exclusivamente los datos del aviso de viaje. */
@Mapper(componentModel = "spring")
public interface AvisoViajeMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fechaRegistro", ignore = true)
    @Mapping(target = "confirmado", constant = "true")
    AvisoViaje toEntity(AvisoViajeRequest request);

    AvisoViajeResponse toResponse(AvisoViaje aviso, String mensajeConfirmacion);
}
