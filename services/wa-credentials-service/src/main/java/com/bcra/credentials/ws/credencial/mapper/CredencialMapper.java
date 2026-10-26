package com.bcra.credentials.ws.credencial.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.bcra.credentials.ws.credencial.dto.ValidarCredencialResponse;
import com.bcra.credentials.ws.credencial.entity.CredencialSesion;

/** Mapea exclusivamente los datos persistidos de una credencial ya validada. */
@Mapper(componentModel = "spring")
public interface CredencialMapper {

    @Mapping(target = "vigente", constant = "true")
    ValidarCredencialResponse toValidarResponse(CredencialSesion credencial);
}
