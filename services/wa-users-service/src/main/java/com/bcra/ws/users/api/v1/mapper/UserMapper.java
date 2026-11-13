package com.bcra.ws.users.api.v1.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.bcra.ws.users.api.v1.dto.UserAuditEventResponse;
import com.bcra.ws.users.api.v1.dto.UserCreateRequest;
import com.bcra.ws.users.api.v1.dto.UserResponse;
import com.bcra.ws.users.domain.UserStatus;
import com.bcra.ws.users.entity.UserAuditEventEntity;
import com.bcra.ws.users.entity.UserEntity;

/**
 * MapStruct mapper Entity ↔ DTO v1. Calcula {@code channelLinkEligible} desde el estado.
 *
 * @since 2026-10-19
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Mapea request de alta a entidad (sin id ni timestamps; status lo setea el service).
     *
     * @param request alta
     * @return entidad parcial
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UserEntity toEntity(UserCreateRequest request);

    /**
     * Mapea entidad a respuesta autorizada.
     *
     * @param entity usuario
     * @return response con elegibilidad de canal
     */
    @Mapping(target = "channelLinkEligible", source = "status", qualifiedByName = "toChannelLinkEligible")
    UserResponse toResponse(UserEntity entity);

    /**
     * Mapea evento de auditoría a response.
     *
     * @param entity evento
     * @return response
     */
    UserAuditEventResponse toAuditResponse(UserAuditEventEntity entity);

    /**
     * Mapea lista de eventos.
     *
     * @param entities eventos
     * @return responses
     */
    List<UserAuditEventResponse> toAuditResponseList(List<UserAuditEventEntity> entities);

    /**
     * Elegibilidad de vínculo canal: solo ACTIVE.
     *
     * @param status estado actual
     * @return true si ACTIVE
     */
    @Named("toChannelLinkEligible")
    default boolean toChannelLinkEligible(UserStatus status) {
        return status == UserStatus.ACTIVE;
    }
}
