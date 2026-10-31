package com.bcra.accounts.ws.cuenta.mapper;

import org.mapstruct.Mapper;

import com.bcra.accounts.ws.cuenta.dto.MovimientoResponse;
import com.bcra.accounts.ws.cuenta.entity.Movimiento;

/** Mapeo del movimiento al contrato público. */
@Mapper(componentModel = "spring")
public interface MovimientoMapper {

    MovimientoResponse toResponse(Movimiento movimiento);
}
