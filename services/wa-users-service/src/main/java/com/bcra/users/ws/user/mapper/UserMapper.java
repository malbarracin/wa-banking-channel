package com.bcra.users.ws.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.bcra.users.ws.user.dto.CreateUserRequest;
import com.bcra.users.ws.user.dto.DocumentoDto;
import com.bcra.users.ws.user.dto.UserResponse;
import com.bcra.users.ws.user.entity.Documento;
import com.bcra.users.ws.user.entity.User;

/** Conversiones entre contratos públicos y entidades del usuario. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

    @Mapping(target = "estado", constant = "ACTIVE")
    @Mapping(target = "nombreTratamiento", ignore = true)
    @Mapping(target = "preferencias", ignore = true)
    User toEntity(CreateUserRequest solicitud);

    UserResponse toResponse(User usuario);

    Documento toEntity(DocumentoDto documento);

    DocumentoDto toResponse(Documento documento);
}
