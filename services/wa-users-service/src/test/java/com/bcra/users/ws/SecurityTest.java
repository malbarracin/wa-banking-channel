package com.bcra.users.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bcra.users.ws.config.CurrentActor;
import com.bcra.users.ws.support.GatewayJwtTestConfig;
import com.bcra.users.ws.support.GatewayJwtTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SecurityTest.TestController.class, GatewayJwtTestConfig.class})
class SecurityTest {
    @Autowired private MockMvc mvc;
    @Autowired private CurrentActor currentActor;

    @RestController
    static class TestController {
        private final CurrentActor actor;
        TestController(CurrentActor actor) { this.actor = actor; }
        @RequestMapping({"/api/v1/users", "/api/v1/users/{id}",
                "/api/v1/users/{id}/status"})
        CurrentActor.Actor handle() { return actor.get().orElseThrow(); }
    }

    @Test
    void missingOrUntrustedIdentityReturnsInvalidToken() throws Exception {
        mvc.perform(get("/api/v1/users/1")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("Identidad requerida"))
                .andExpect(jsonPath("$.details").isArray()).andExpect(jsonPath("$.timestamp").isString());
        mvc.perform(get("/api/v1/users/1").header("X-Actor-Id", "actor").header("X-Actor-Role", "OPERACIONES"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mvc.perform(get("/api/v1/users/1").header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mvc.perform(get("/api/v1/users/1").header("Authorization", "Token x"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void expiredTokenAndInsufficientClaimsAreMapped() throws Exception {
        mvc.perform(get("/api/v1/users/1").header("Authorization", GatewayJwtTokens.bearerExpired("actor", "OPERACIONES")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        mvc.perform(get("/api/v1/users/1").header("Authorization", GatewayJwtTokens.bearer("actor", "OTRO")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INSUFFICIENT_CLAIMS"));
    }

    @Test
    void rolesEnforceEndpointMatrixFromJwtClaims() throws Exception {
        mvc.perform(post("/api/v1/users").header("Authorization", GatewayJwtTokens.bearer("actor", "SOPORTE")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INSUFFICIENT_CLAIMS"))
                .andExpect(jsonPath("$.message").value("Acceso denegado"))
                .andExpect(jsonPath("$.details").isArray()).andExpect(jsonPath("$.timestamp").isString());
        mvc.perform(get("/api/v1/users/1").header("Authorization", GatewayJwtTokens.bearer("actor", "SOPORTE")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/00000000-0000-0000-0000-000000000001/audit")
                .header("Authorization", GatewayJwtTokens.bearer("actor", "SOPORTE")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/users/by-document")
                .header("Authorization", GatewayJwtTokens.bearer("actor", "SERVICIO_INTERNO")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users/1/audit")
                .header("Authorization", GatewayJwtTokens.bearer("actor", "SERVICIO_INTERNO")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/users/1/status")
                .header("Authorization", GatewayJwtTokens.bearer("actor", "OPERACIONES")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/users")
                .header("Authorization", GatewayJwtTokens.bearer("actor", "OPERACIONES")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/users")
                .header("Authorization", GatewayJwtTokens.bearer("actor", "SOPORTE"))
                .header("X-Actor-Id", "spoof").header("X-Actor-Role", "OPERACIONES"))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthIsPublicAndActorDoesNotLeak() throws Exception {
        SecurityContextHolder.clearContext();
        assertThat(currentActor.get()).isEmpty();
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        assertThat(currentActor.get()).isEmpty();
    }
}
