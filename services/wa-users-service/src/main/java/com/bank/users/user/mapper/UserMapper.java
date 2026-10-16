package com.bank.users.user.mapper;

import java.util.List;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.audit.entity.AuditEntry;
import com.bank.users.user.dto.UserAuditResponse;
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.entity.User;

/** Mapeo de los contratos HTTP sin exponer la entidad persistente. */
@Mapper(componentModel = "spring")
public interface UserMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "tipoDocumento", source = "tipoDocumento")
    @Mapping(target = "numeroDocumento", source = "numeroDocumento")
    @Mapping(target = "nombreTratamiento", source = "nombreTratamiento")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "telefonoContacto", source = "telefonoContacto")
    User toEntity(CreateUserRequest request);

    default UserResponse toResponse(User user) {
        return toResponse(user, List.of());
    }

    @Mapping(target = "historialResumido", source = "history")
    @Mapping(target = "aptoParaVinculoCanal",
            expression = "java(user.getStatus() == com.bank.users.user.entity.UserStatus.ACTIVO)")
    UserResponse toResponse(User user, List<AuditEntry> history);

    UserAuditResponse toAuditResponse(AuditEntry entry);
}
