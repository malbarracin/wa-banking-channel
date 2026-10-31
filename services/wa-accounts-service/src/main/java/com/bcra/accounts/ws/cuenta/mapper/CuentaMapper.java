package com.bcra.accounts.ws.cuenta.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import com.bcra.accounts.ws.cuenta.dto.CuentaResponse;
import com.bcra.accounts.ws.cuenta.dto.SaldoResponse;
import com.bcra.accounts.ws.cuenta.entity.Cuenta;

/** Proyecta exclusivamente los campos públicos de las cuentas. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CuentaMapper {

    CuentaResponse toResponse(Cuenta cuenta);

    SaldoResponse toSaldoResponse(Cuenta cuenta);
}
