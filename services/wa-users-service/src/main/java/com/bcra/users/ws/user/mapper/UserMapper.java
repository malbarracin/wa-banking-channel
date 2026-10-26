package com.bcra.users.ws.user.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.bcra.users.ws.user.dto.CreateUserRequest;
import com.bcra.users.ws.user.dto.DocumentoDto;
import com.bcra.users.ws.user.dto.DocumentoResponse;
import com.bcra.users.ws.user.dto.UpdateUserRequest;
import com.bcra.users.ws.user.dto.UserResponse;
import com.bcra.users.ws.user.dto.UserStatusResponse;
import com.bcra.users.ws.user.entity.Documento;
import com.bcra.users.ws.user.entity.User;

/** Conversiones entre contratos públicos y entidades del usuario. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

    @Mapping(target = "estado", constant = "ACTIVE")
    @Mapping(target = "nombreTratamiento", ignore = true)
    @Mapping(target = "preferencias", ignore = true)
    User toEntity(CreateUserRequest solicitud);

    /** Lista explícita de propiedades editables; nunca copia identidad ni estado. */
    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "email", source = "email")
    @Mapping(target = "telefono", source = "telefono")
    @Mapping(target = "nombreTratamiento", source = "nombreTratamiento")
    @Mapping(target = "preferencias", source = "preferencias")
    void actualizar(UpdateUserRequest request, @MappingTarget User usuario);

    UserResponse toResponse(User usuario);

    Documento toEntity(DocumentoDto documento);

    UserStatusResponse toStatusResponse(User usuario);

    DocumentoResponse toDocumentoResponse(Documento documento);
}
