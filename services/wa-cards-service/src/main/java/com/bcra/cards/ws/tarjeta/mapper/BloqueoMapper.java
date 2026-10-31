package com.bcra.cards.ws.tarjeta.mapper;

import com.bcra.cards.ws.tarjeta.dto.BloqueoTemporalResponse;
import com.bcra.cards.ws.tarjeta.entity.Tarjeta;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Proyecta el resultado sin exponer datos sensibles de la tarjeta. */
@Mapper(componentModel = "spring")
public interface BloqueoMapper {

    @Mapping(target = "tarjetaId", source = "id")
    @Mapping(target = "mensaje", constant = "Tarjeta bloqueada temporalmente. Para desbloquearla, indicá DESBLOQUEAR.")
    BloqueoTemporalResponse toResponse(Tarjeta tarjeta);
}
