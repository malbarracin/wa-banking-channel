package com.bcra.users.ws.audit.mapper;

import java.util.List;
import java.util.Set;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.bcra.users.ws.audit.dto.AuditEventResponse;
import com.bcra.users.ws.audit.entity.AuditEvent;

@Mapper(componentModel = "spring")
public interface AuditMapper {
    Set<String> SAFE_FIELDS = Set.of("tipoDocumento", "numeroDocumento", "nombreCompleto", "nombreTratamiento",
            "fechaNacimiento", "email", "telefono", "preferencias", "estado");

    @Mapping(target = "fecha", source = "createdAt")
    @Mapping(target = "resumen", source = "detalle", qualifiedByName = "safeFields")
    AuditEventResponse toResponse(AuditEvent event);

    @Named("safeFields")
    default List<String> safeFields(List<String> fields) {
        return fields == null ? List.of() : fields.stream().filter(SAFE_FIELDS::contains).toList();
    }
}
