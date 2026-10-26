package com.bcra.users.ws.user.dto;

import com.bcra.users.ws.user.entity.UserStatus;

/** Estado solicitado; debe estar presente y representar una transición soportada desde el estado actual. */
public record UpdateStatusRequest(UserStatus estado) {
}
