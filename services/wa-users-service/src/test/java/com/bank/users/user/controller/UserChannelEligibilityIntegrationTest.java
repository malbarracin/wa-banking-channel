package com.bank.users.user.controller;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserChannelEligibilityIntegrationTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository repository;

    @Autowired
    UserChannelEligibilityIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, UserRepository repository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    @Test
    @DisplayName("La elegibilidad sigue alta, suspensión, reactivación y baja sin exponer datos personales")
    void consultaCicloCompletoConContratoMinimo() throws Exception {
        String created = mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tipoDocumento":"DNI","numeroDocumento":"%s","nombreTratamiento":"Cliente",
                        "email":"cliente@example.com","telefonoContacto":"123456789"}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(created).get("id").asText());
        mockMvc.perform(patch("/api/v1/users/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"preferencias":{"idioma":"es"}}
                        """))
                .andExpect(status().isOk());

        assertEligibility(id, UserStatus.ACTIVO);
        for (UserStatus target : new UserStatus[] {
                UserStatus.SUSPENDIDO, UserStatus.ACTIVO, UserStatus.BAJA_LOGICA}) {
            mockMvc.perform(patch("/api/v1/users/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"" + target + "\"}"))
                    .andExpect(status().isOk());
            assertEligibility(id, target);
        }
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(UserStatus.BAJA_LOGICA);
    }

    @Test
    @DisplayName("Un usuario inexistente devuelve 404 con el contrato uniforme")
    void rechazaUsuarioInexistente() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}/channel-eligibility", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    @DisplayName("Un UUID inválido devuelve 400 BAD_REQUEST con el contrato uniforme")
    void rechazaUuidInvalido() throws Exception {
        mockMvc.perform(get("/api/v1/users/invalido/channel-eligibility"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.length()").value(4));
    }

    private void assertEligibility(UUID id, UserStatus expectedStatus) throws Exception {
        String body = mockMvc.perform(get("/api/v1/users/{id}/channel-eligibility", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(id.toString()))
                .andExpect(jsonPath("$.status").value(expectedStatus.name()))
                .andExpect(jsonPath("$.aptoParaVinculoCanal").value(expectedStatus == UserStatus.ACTIVO))
                .andReturn().getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("userId", "status", "aptoParaVinculoCanal");
    }
}
