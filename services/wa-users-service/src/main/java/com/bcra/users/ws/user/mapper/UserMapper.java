package com.bcra.users.ws.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import com.bcra.users.ws.user.dto.CreateUserRequest;
import com.bcra.users.ws.user.dto.UserResponse;
import com.bcra.users.ws.user.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "nombreTratamiento", ignore = true)
    @Mapping(target = "preferencias", ignore = true)
    @Mapping(target = "estado", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    User toEntity(CreateUserRequest request);

    @Mapping(target = "numeroDocumentoEnmascarado", expression = "java(mask(user.getNumeroDocumento()))")
    UserResponse toResponse(User user);

    @Named("maskDocument")
    default String mask(String document) {
        return document.length() <= 4 ? "***" : "***" + document.substring(document.length() - 4);
    }
}
