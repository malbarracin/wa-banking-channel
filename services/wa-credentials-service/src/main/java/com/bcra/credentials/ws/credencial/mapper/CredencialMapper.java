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

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "jti", source = "jti")
    @Mapping(target = "estado", constant = "ACTIVA")
    @Mapping(target = "fechaEmision", source = "fechaEmision")
    @Mapping(target = "fechaExpiracion", source = "fechaExpiracion")
    @Mapping(target = "fechaRevocacion", ignore = true)
    @Mapping(target = "motivoRevocacion", ignore = true)
    @Mapping(target = "revocadaPor", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CredencialSesion toRenovada(CredencialSesion anterior, String jti, Instant fechaEmision,
            Instant fechaExpiracion);
}
