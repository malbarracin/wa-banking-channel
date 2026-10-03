package com.bank.users.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.entity.User;

/**
 * Mapeo común entre {@link User} y sus DTOs de alta y consulta.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

    /**
     * Convierte un usuario en su proyección pública.
     *
     * @param user usuario persistido
     * @return respuesta común
     */
    UserResponse toResponse(User user);

    /**
     * Crea una entidad a partir de una solicitud de alta. No asigna {@code id} ni {@code status}
     * (los define el caso de uso); los timestamps se fijan al persistir.
     *
     * @param request solicitud de alta validada
     * @return entidad nueva sin persistir
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    User toEntity(CreateUserRequest request);
}
