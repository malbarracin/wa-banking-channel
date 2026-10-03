package com.bank.users.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bank.users.exception.UserNotFoundException;
import com.bank.users.user.dto.EligibilityResponse;
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.service.UserService;

@WebMvcTest(UserController.class)
class UserControllerTest {

    private static final UserResponse USER = new UserResponse(1L, DocumentType.DNI, "30123456", "Juan", "Perez",
            "Juancho", "juan@example.com", LocalDate.of(1990, 5, 20), UserStatus.SUSPENDED,
            Instant.parse("2026-10-03T10:00:00Z"), Instant.parse("2026-10-03T11:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("GET /{id} devuelve 200 con UserResponse sin JWT")
    void getByIdOk() throws Exception {
        when(userService.findById(1L)).thenReturn(USER);

        mockMvc.perform(get("/api/v1/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.documentType").value("DNI"))
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-03T10:00:00Z"));
    }

    @Test
    @DisplayName("GET /{id} inexistente devuelve 404 NOT_FOUND")
    void getByIdNotFound() throws Exception {
        when(userService.findById(9L)).thenThrow(UserNotFoundException.byId(9L));

        mockMvc.perform(get("/api/v1/users/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /{id} con id no numérico devuelve 400")
    void getByIdInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/users/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("GET por documento devuelve 200 con UserResponse")
    void getByDocumentOk() throws Exception {
        when(userService.findByDocument(DocumentType.DNI, "30123456")).thenReturn(USER);

        mockMvc.perform(get("/api/v1/users").param("documentType", "DNI").param("documentNumber", "30123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.documentNumber").value("30123456"));
    }

    @Test
    @DisplayName("GET por documento inexistente devuelve 404 NOT_FOUND")
    void getByDocumentNotFound() throws Exception {
        when(userService.findByDocument(DocumentType.CUIT, "1")).thenThrow(UserNotFoundException.byDocument());

        mockMvc.perform(get("/api/v1/users").param("documentType", "CUIT").param("documentNumber", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("GET por documento sin parámetros obligatorios devuelve 400 VALIDATION_ERROR")
    void getByDocumentMissingParams() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("documentNumber", "30123456"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("documentType"));
        mockMvc.perform(get("/api/v1/users").param("documentType", "DNI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("documentNumber"));
        verify(userService, never()).findByDocument(any(), anyString());
    }

    @Test
    @DisplayName("GET por documento con número en blanco devuelve 400 VALIDATION_ERROR")
    void getByDocumentBlankNumber() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("documentType", "DNI").param("documentNumber", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("documentNumber"));
    }

    @Test
    @DisplayName("GET por documento con tipo no admitido devuelve 400 BAD_REQUEST")
    void getByDocumentInvalidType() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("documentType", "LIBRETA").param("documentNumber", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details[0].field").value("documentType"));
    }

    @Test
    @DisplayName("GET /{id}/eligible devuelve 200 con exactamente userId y eligible")
    void eligibilityOk() throws Exception {
        when(userService.checkEligibility(1L)).thenReturn(new EligibilityResponse(1L, true));

        mockMvc.perform(get("/api/v1/users/1/eligible"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.eligible").value(true));
    }

    @Test
    @DisplayName("GET /{id}/eligible de un id inexistente devuelve 404 NOT_FOUND")
    void eligibilityNotFound() throws Exception {
        when(userService.checkEligibility(9L)).thenThrow(UserNotFoundException.byId(9L));

        mockMvc.perform(get("/api/v1/users/9/eligible"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
