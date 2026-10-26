package com.bcra.users.ws.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import com.bcra.users.ws.user.dto.DocumentoResponse;
import com.bcra.users.ws.user.dto.UserResponse;
import com.bcra.users.ws.user.dto.UserStatusResponse;
import com.bcra.users.ws.user.entity.Documento;
import com.bcra.users.ws.user.entity.User;

/** Conversión del dominio a los contratos de consulta, sin exponer entidades persistidas. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

    UserResponse toResponse(User usuario);

    UserStatusResponse toStatusResponse(User usuario);

    DocumentoResponse toDocumentoResponse(Documento documento);
}
