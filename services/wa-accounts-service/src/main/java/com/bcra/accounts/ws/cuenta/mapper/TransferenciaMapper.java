package com.bcra.accounts.ws.cuenta.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.bcra.accounts.ws.cuenta.dto.TransferenciaPreparadaResponse;
import com.bcra.accounts.ws.cuenta.entity.Transferencia;

/** Proyección del dominio sin exponer entidades ni documentos del titular. */
@Mapper(componentModel = "spring")
public interface TransferenciaMapper {

    @Mapping(target = "transferenciaId", source = "transferencia.id")
    @Mapping(target = "requiereDobleConfirmacion", source = "transferencia.dobleConfirmacionRequerida")
    TransferenciaPreparadaResponse toPreparadaResponse(Transferencia transferencia, String resumen);
}

