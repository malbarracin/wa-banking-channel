package com.bcra.ws.users.api.v1.mapper;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.bcra.ws.users.api.v1.dto.UserResponse;

/** Política de consulta; el origen declarado no constituye autenticación del solicitante. */
@Component
public class UserQueryProjection {

    private static final Set<String> INTERNAL_ACTORS = Set.of("BUSINESS_OPERATIONS", "SUPPORT");

    private final UserMapper userMapper;
    private final Set<String> fullViewActors;

    /** Configura qué actores internos conservan el dataset completo. */
    public UserQueryProjection(UserMapper userMapper,
            @Value("${users.query.full-view-actors:BUSINESS_OPERATIONS,SUPPORT}") Set<String> fullViewActors) {
        this.userMapper = userMapper;
        this.fullViewActors = Set.copyOf(fullViewActors);
    }

    /** Ausentes, desconocidos y servicios bancarios siempre reciben el subset básico. */
    public Object project(UserResponse user, String actor) {
        if (actor != null && INTERNAL_ACTORS.contains(actor) && fullViewActors.contains(actor)) {
            return user;
        }
        return userMapper.toBasicResponse(user);
    }
}
