package com.bcra.users.ws.user.service;

import java.util.UUID;
import com.bcra.users.ws.user.entity.UserStatus;

public record StatusChangedEvent(UUID userId, UserStatus estadoAnterior, UserStatus estadoNuevo,
        String correlationId) { }
