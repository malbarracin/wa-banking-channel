package com.bank.users.user.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.user.dto.UpdateUserRequest;
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.entity.User;

/**
 * Mapeo común entre {@link User} y sus DTOs de alta, actualización y consulta.
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

    /**
     * Aplica sobre la entidad solo los datos permitidos informados ({@code displayName}, {@code email}).
     * Los valores {@code null} se ignoran y conservan el valor actual; la identidad legal, el estado y
     * los timestamps nunca se modifican por este mapeo.
     *
     * @param request solicitud de actualización validada
     * @param user    entidad persistida a actualizar
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "documentType", ignore = true)
    @Mapping(target = "documentNumber", ignore = true)
    @Mapping(target = "firstName", ignore = true)
    @Mapping(target = "lastName", ignore = true)
    @Mapping(target = "birthDate", ignore = true)
    @Mapping(target = "status", ignore = true)
    void updateEntity(UpdateUserRequest request, @MappingTarget User user);
}
