package com.bcra.ws.users.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.bcra.ws.users.exception.DuplicateEmailException;
import com.bcra.ws.users.service.UserService;

/** Verifica PATCH con Jackson configurado para ignorar campos desconocidos. */
@WebMvcTest(controllers = UserController.class,
        properties = "spring.jackson.deserialization.fail-on-unknown-properties=false")
class UserPatchContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @ParameterizedTest
    @ValueSource(strings = {"documentType", "documentNumber", "firstName", "lastName", "birthDate", "status", "other"})
    @DisplayName("PATCH rechaza campos no actualizables sin invocar el servicio")
    void shouldRejectForbiddenFields(String field) throws Exception {
        mockMvc.perform(patch("/api/v1/users/id").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredName\":\"Ana\",\"" + field + "\":\"value\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value(field))
                .andExpect(jsonPath("$.details[0].message").value("Field is not updatable in this service"));
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("POST y PATCH responden 409 con field=email")
    void shouldExposeEmailConflict() throws Exception {
        when(userService.update(any(), any(), any())).thenThrow(new DuplicateEmailException());
        when(userService.create(any(), any())).thenThrow(new DuplicateEmailException());
        mockMvc.perform(patch("/api/v1/users/id").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"taken@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details[0].field").value("email"));
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("""
                {"documentType":"DNI","documentNumber":"12345678","firstName":"Ana","lastName":"Perez",
                 "email":"taken@example.com","phone":"+54911111111","address":"Street 123",
                 "birthDate":"1990-05-20"}
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[0].field").value("email"));
    }
}
