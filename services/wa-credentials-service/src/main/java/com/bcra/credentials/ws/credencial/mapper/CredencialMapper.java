package com.bcra.credentials.ws.credencial.mapper;

import java.time.Instant;
import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.bcra.credentials.ws.credencial.dto.EmitirCredencialRequest;
import com.bcra.credentials.ws.credencial.dto.EmitirCredencialResponse;
import com.bcra.credentials.ws.credencial.dto.ValidarCredencialResponse;
import com.bcra.credentials.ws.credencial.entity.CredencialSesion;

/** Conversiones de emisión y de datos persistidos de una credencial ya validada. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CredencialMapper {

    @Mapping(target = "vigente", constant = "true")
    ValidarCredencialResponse toValidarResponse(CredencialSesion credencial);

    @Mapping(target = "estado", constant = "ACTIVA")
    CredencialSesion toEntity(EmitirCredencialRequest request, String jti, UUID vinculoId,
            Instant fechaEmision, Instant fechaExpiracion);

    EmitirCredencialResponse toResponse(CredencialSesion sesion, String credencial);
}
