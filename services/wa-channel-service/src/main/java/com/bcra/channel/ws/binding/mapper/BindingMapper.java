package com.bcra.channel.ws.binding.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.bcra.channel.ws.binding.dto.BindingStatusResponse;
import com.bcra.channel.ws.binding.dto.ChannelProfileResponse;
import com.bcra.channel.ws.binding.dto.VerifiableResultResponse;
import com.bcra.channel.ws.binding.entity.Binding;
import com.bcra.channel.ws.binding.entity.BindingStatus;

/**
 * Mapeo Entity ↔ DTO del módulo de vínculo.
 */
@Mapper(componentModel = "spring")
public interface BindingMapper {

    @Mapping(target = "bindingId", source = "id")
    BindingStatusResponse toStatusResponse(Binding binding);

    /**
     * Resultado verificable tras activar el vínculo.
     *
     * @param binding vínculo en {@link BindingStatus#ACTIVO}
     * @return payload para el orquestador
     */
    @Mapping(target = "identityVerified", constant = "true")
    @Mapping(target = "bindingId", source = "id")
    VerifiableResultResponse toVerifiableResult(Binding binding);

    /**
     * Perfil del canal a partir del vínculo y el nombre público de H1.
     *
     * @param binding     vínculo activo
     * @param displayName nombre para mostrar (sin datos legales)
     * @return perfil del canal
     */
    @Mapping(target = "whatsappNumber", source = "binding.whatsappNumber")
    @Mapping(target = "clientId", source = "binding.clientId")
    @Mapping(target = "bindingStatus", source = "binding.status")
    @Mapping(target = "languagePreference", source = "binding.languagePreference")
    @Mapping(target = "notificationsEnabled", source = "binding.notificationsEnabled")
    @Mapping(target = "createdAt", source = "binding.createdAt")
    @Mapping(target = "updatedAt", source = "binding.updatedAt")
    @Mapping(target = "displayName", source = "displayName")
    ChannelProfileResponse toProfileResponse(Binding binding, String displayName);

    /**
     * Respuesta virtual cuando no hay registro de vínculo.
     *
     * @param whatsappNumber número consultado
     * @return status {@link BindingStatus#SIN_VINCULO} sin clientId ni bindingId
     */
    default BindingStatusResponse unboundStatus(String whatsappNumber) {
        return new BindingStatusResponse(whatsappNumber, BindingStatus.SIN_VINCULO, null, null);
    }
}
