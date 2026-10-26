package com.bcra.users.ws.user.dto;

import com.bcra.users.ws.user.entity.UserStatus;

/** Documento y estado actual para que los servicios de canal evalúen la habilitación de nuevos vínculos. */
public record UserStatusResponse(DocumentoResponse documento, UserStatus estado) {
}
