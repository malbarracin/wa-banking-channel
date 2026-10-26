package com.bcra.channel.ws.vinculo.mapper;

import java.util.List;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.bcra.channel.ws.vinculo.dto.ActualizarPreferenciasRequest;
import com.bcra.channel.ws.vinculo.dto.EventoHistorialResponse;
import com.bcra.channel.ws.vinculo.dto.HistorialVinculoResponse;
import com.bcra.channel.ws.vinculo.dto.PreferenciasResponse;
import com.bcra.channel.ws.vinculo.dto.VinculoResponse;
import com.bcra.channel.ws.vinculo.entity.Preferencias;
import com.bcra.channel.ws.vinculo.entity.Vinculo;
import com.bcra.channel.ws.vinculo.entity.VinculoAuditLog;

/** Proyecta únicamente los datos públicos del vínculo. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface VinculoMapper {

    VinculoResponse toResponse(Vinculo vinculo);

    PreferenciasResponse toPreferenciasResponse(Preferencias preferencias);

    /**
     * Aplica solo los campos presentes del request; los null no pisan el valor embebido.
     *
     * @param request cambios solicitados
     * @param preferencias destino embebido del vínculo
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void actualizarPreferencias(ActualizarPreferenciasRequest request, @MappingTarget Preferencias preferencias);

    EventoHistorialResponse toEventoResponse(VinculoAuditLog evento);

    @Mapping(target = "estadoActual", source = "vinculo.estado")
    @Mapping(target = "eventos", source = "eventos")
    HistorialVinculoResponse toHistorialResponse(Vinculo vinculo, List<VinculoAuditLog> eventos);
}
