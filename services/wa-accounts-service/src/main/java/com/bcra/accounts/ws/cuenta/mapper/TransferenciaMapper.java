package com.bcra.accounts.ws.cuenta.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.bcra.accounts.ws.cuenta.dto.TransferenciaPreparadaResponse;
import com.bcra.accounts.ws.cuenta.dto.ComprobanteResponse;
import com.bcra.accounts.ws.cuenta.dto.TransferenciaResponse;
import com.bcra.accounts.ws.cuenta.entity.Transferencia;

/** Proyección del dominio sin exponer entidades ni documentos del titular. */
@Mapper(componentModel = "spring")
public interface TransferenciaMapper {

    @Mapping(target = "transferenciaId", source = "transferencia.id")
    @Mapping(target = "referencia", source = "transferencia.referencia")
    TransferenciaResponse toResponse(Transferencia transferencia, ComprobanteResponse comprobante);

    @Mapping(target = "fecha", source = "transferencia.fechaEjecucion")
    @Mapping(target = "origen", source = "transferencia.cuentaOrigen.numeroCuenta")
    @Mapping(target = "destino", source = "destino")
    ComprobanteResponse toComprobante(Transferencia transferencia, String destino);

    @Mapping(target = "transferenciaId", source = "transferencia.id")
    @Mapping(target = "requiereDobleConfirmacion", source = "transferencia.dobleConfirmacionRequerida")
    TransferenciaPreparadaResponse toPreparadaResponse(Transferencia transferencia, String resumen);
}

