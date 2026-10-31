package com.bcra.credentials.ws.config;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bcra.credentials.ws.exception.UnauthorizedException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CallerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    static Stream<Arguments> operations() {
        return Stream.of(
                Arguments.of("/emitir", "h3-test-token", "bank-test-token"),
                Arguments.of("/+5491123456789/revocacion", "h3-test-token", "bank-test-token"),
                Arguments.of("/revocacion-masiva", "bank-test-token", "h3-test-token"));
    }

    @ParameterizedTest
    @MethodSource("operations")
    @DisplayName("Rechaza ausencia, secreto inválido y actor incorrecto antes de procesar datos")
    void rejectsUnauthorizedCallers(String path, String allowed, String forbidden) throws Exception {
        String body = "{\"vinculoActivo\":true,\"identidadVerificada\":true}";
        mockMvc.perform(post("/api/v1/credenciales" + path)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/v1/credenciales" + path)
                        .header("Authorization", "Bearer unknown")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/credenciales" + path)
                        .header("Authorization", "Bearer " + forbidden)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/v1/credenciales" + path)
                        .header("Authorization", "Bearer " + allowed)
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Sin secretos no se autoriza; secretos compartidos entre actores impiden iniciar")
    void rejectsMissingOrSharedSecrets() {
        var empty = new CallerAuthorization("", "");
        assertThatThrownBy(() -> empty.requireActor("Bearer ", false))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> empty.requireActor("Basic abc", true))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> new CallerAuthorization("same", "same"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new CallerAuthorization("h3", "bank").requireActor("Bearer h3", false))
                .doesNotThrowAnyException();
        assertThatCode(() -> new CallerAuthorization("h3", "bank").requireActor("Bearer bank", true))
                .doesNotThrowAnyException();
    }
}
