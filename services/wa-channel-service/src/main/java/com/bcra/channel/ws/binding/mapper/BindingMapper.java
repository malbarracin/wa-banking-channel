package com.bcra.channel.ws.binding.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.bcra.channel.ws.binding.dto.BindingStatusResponse;
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
     * Respuesta virtual cuando no hay registro de vínculo.
     *
     * @param whatsappNumber número consultado
     * @return status {@link BindingStatus#SIN_VINCULO} sin clientId ni bindingId
     */
    default BindingStatusResponse unboundStatus(String whatsappNumber) {
        return new BindingStatusResponse(whatsappNumber, BindingStatus.SIN_VINCULO, null, null);
    }
}
