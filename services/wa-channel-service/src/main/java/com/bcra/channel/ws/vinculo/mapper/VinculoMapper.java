package com.bcra.channel.ws.vinculo.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.bcra.channel.ws.vinculo.client.ClientePerfilResponse;
import com.bcra.channel.ws.vinculo.dto.EventoHistorialResponse;
import com.bcra.channel.ws.vinculo.dto.HistorialVinculoResponse;
import com.bcra.channel.ws.vinculo.dto.PerfilCanalResponse;
import com.bcra.channel.ws.vinculo.dto.PreferenciasResponse;
import com.bcra.channel.ws.vinculo.dto.VinculoEstadoResponse;
import com.bcra.channel.ws.vinculo.dto.VinculoResponse;
import com.bcra.channel.ws.vinculo.entity.Preferencias;
import com.bcra.channel.ws.vinculo.entity.Vinculo;
import com.bcra.channel.ws.vinculo.entity.VinculoAuditLog;

/** Proyecta únicamente los datos públicos del vínculo. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface VinculoMapper {

    VinculoResponse toResponse(Vinculo vinculo);

    @Mapping(target = "vinculado", constant = "true")
    VinculoEstadoResponse toEstadoResponse(Vinculo vinculo);

    PreferenciasResponse toPreferenciasResponse(Preferencias preferencias);

    @Mapping(target = "preferencias", source = "preferencias")
    PerfilCanalResponse toPerfilCanalResponse(ClientePerfilResponse perfil, Preferencias preferencias);

    EventoHistorialResponse toEventoResponse(VinculoAuditLog evento);

    @Mapping(target = "estadoActual", source = "vinculo.estado")
    @Mapping(target = "eventos", source = "eventos")
    HistorialVinculoResponse toHistorialResponse(Vinculo vinculo, List<VinculoAuditLog> eventos);
}
