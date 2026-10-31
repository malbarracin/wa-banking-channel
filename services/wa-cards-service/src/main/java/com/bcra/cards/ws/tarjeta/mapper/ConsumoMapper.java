package com.bcra.cards.ws.tarjeta.mapper;

import com.bcra.cards.ws.tarjeta.dto.ConsumoResponse;
import com.bcra.cards.ws.tarjeta.entity.Consumo;
import org.mapstruct.Mapper;

/** Proyección de consumos al contrato público. */
@Mapper(componentModel = "spring")
public interface ConsumoMapper {

    ConsumoResponse toResponse(Consumo consumo);
}
