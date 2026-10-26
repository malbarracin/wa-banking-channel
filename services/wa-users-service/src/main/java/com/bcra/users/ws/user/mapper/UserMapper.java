package com.bcra.users.ws.user.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.bcra.users.ws.user.dto.DocumentoResponse;
import com.bcra.users.ws.user.dto.UpdateUserRequest;
import com.bcra.users.ws.user.dto.UserResponse;
import com.bcra.users.ws.user.dto.UserStatusResponse;
import com.bcra.users.ws.user.entity.Documento;
import com.bcra.users.ws.user.entity.User;

/** Conversión del dominio a los contratos de consulta, sin exponer entidades persistidas. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

    /** Lista explícita de propiedades editables; nunca copia identidad ni estado. */
    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "email", source = "email")
    @Mapping(target = "telefono", source = "telefono")
    @Mapping(target = "nombreTratamiento", source = "nombreTratamiento")
    @Mapping(target = "preferencias", source = "preferencias")
    void actualizar(UpdateUserRequest request, @MappingTarget User usuario);

    UserResponse toResponse(User usuario);

    UserStatusResponse toStatusResponse(User usuario);

    DocumentoResponse toDocumentoResponse(Documento documento);
}
