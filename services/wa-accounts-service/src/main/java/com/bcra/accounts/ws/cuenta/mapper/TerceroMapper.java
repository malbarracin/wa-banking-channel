package com.bcra.accounts.ws.cuenta.mapper;

import org.mapstruct.Mapper;

import com.bcra.accounts.ws.cuenta.dto.TerceroResponse;
import com.bcra.accounts.ws.cuenta.entity.TerceroAgendado;

/** Proyecta la agenda sin exponer el documento del titular. */
@Mapper(componentModel = "spring")
public interface TerceroMapper {

    TerceroResponse toResponse(TerceroAgendado tercero);
}
