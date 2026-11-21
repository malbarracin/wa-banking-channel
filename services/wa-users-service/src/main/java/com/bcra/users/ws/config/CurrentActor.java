package com.bcra.users.ws.config;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentActor {
    public record Actor(String id, String role) { }

    public Optional<Actor> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Actor actor)) {
            return Optional.empty();
        }
        return Optional.of(actor);
    }
}
