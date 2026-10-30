package com.bcra.cards.ws.tarjeta.mapper;

import com.bcra.cards.ws.tarjeta.dto.TarjetaResponse;
import com.bcra.cards.ws.tarjeta.entity.Tarjeta;
import org.mapstruct.Mapper;

/** Proyecta únicamente los datos públicos de la tarjeta. */
@Mapper(componentModel = "spring")
public interface TarjetaMapper {

    TarjetaResponse toResponse(Tarjeta tarjeta);
}
