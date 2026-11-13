package com.bcra.ws.users.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bcra.ws.users.api.v1.mapper.UserMapper;
import com.bcra.ws.users.domain.AuditAction;
import com.bcra.ws.users.domain.AuditResult;
import com.bcra.ws.users.domain.DocumentType;
import com.bcra.ws.users.entity.UserEntity;
import com.bcra.ws.users.exception.GlobalExceptionHandler;
import com.bcra.ws.users.repository.UserRepository;
import com.bcra.ws.users.service.UserAuditService;
import com.bcra.ws.users.service.UserServiceImpl;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class UserPatchServiceContractTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserAuditService userAuditService;

    private MockMvc mockMvc;

    @BeforeEach
    void configurePermissiveJacksonAndRealService() {
        var mapper = JsonMapper.builder().findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        var service = new UserServiceImpl(userRepository, userMapper, userAuditService);
        var projection = new com.bcra.ws.users.api.v1.mapper.UserQueryProjection(
                userMapper, java.util.Set.of("BUSINESS_OPERATIONS", "SUPPORT"));
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(service, projection))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"documentType", "documentNumber", "firstName", "lastName", "birthDate",
            "status", "unknown", "legalAddress"})
    @DisplayName("PATCH rechaza campos prohibidos aun con Jackson permisivo y valores nulos")
    void shouldRejectForbiddenFieldsBeforeAccessingPersistence(String field) throws Exception {
        for (String value : new String[] {"null", "\"changed\"", "{}"}) {
            mockMvc.perform(patch("/api/v1/users/user-1").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"preferredName\":\"Changed\",\"" + field + "\":" + value + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.details[0].field").value(field))
                    .andExpect(jsonPath("$.details[0].message")
                            .value("Field is not updatable in this service"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
        verifyNoInteractions(userRepository, userMapper, userAuditService);
    }

    @Test
    @DisplayName("PATCH vacío atraviesa el servicio real y devuelve BAD_REQUEST sin guardar")
    void shouldRejectEmptyPatch() throws Exception {
        mockMvc.perform(patch("/api/v1/users/user-1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray());
        verifyNoInteractions(userRepository, userMapper, userAuditService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"uk_email", "uk_document"})
    @DisplayName("PATCH traduce carreras de cada índice a HTTP 409 y audita FAILURE con actor")
    void shouldTranslateIndexRaceThroughHttp(String index) throws Exception {
        UserEntity original = UserEntity.builder().id("user-1").documentType(DocumentType.DNI)
                .documentNumber("12345678").email("original@example.com").address("Original").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(original));
        when(userRepository.save(any())).thenThrow(new DuplicateKeyException(
                "E11000 duplicate key error collection: users index: " + index + " dup key: {}"));

        mockMvc.perform(patch("/api/v1/users/user-1").header("X-Actor-Id", "editor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"address\":\"Changed\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details[0].field")
                        .value(index.equals("uk_email") ? "email" : "documentNumber"));
        org.assertj.core.api.Assertions.assertThat(original.getEmail()).isEqualTo("original@example.com");
        org.assertj.core.api.Assertions.assertThat(original.getAddress()).isEqualTo("Original");
        verify(userAuditService).record(eq("user-1"), eq(AuditAction.USER_UPDATED),
                eq(AuditResult.FAILURE), any(), eq("editor"));
        verify(userMapper, never()).toResponse(any());
    }
}
