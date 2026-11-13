package com.bcra.ws.users.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.bcra.ws.users.api.v1.mapper.UserMapperImpl;
import com.bcra.ws.users.api.v1.mapper.UserQueryProjection;
import com.bcra.ws.users.domain.DocumentType;
import com.bcra.ws.users.domain.UserStatus;
import com.bcra.ws.users.entity.UserEntity;
import com.bcra.ws.users.repository.UserRepository;
import com.bcra.ws.users.service.UserAuditService;
import com.bcra.ws.users.service.UserServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Contrato HTTP de consulta con servicio y mapeo reales, sin requerir Mongo externo. */
@WebMvcTest(UserController.class)
@Import({UserServiceImpl.class, UserMapperImpl.class, UserQueryProjection.class})
class UserQueryContractTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private UserAuditService userAuditService;

    static Stream<Arguments> queryViews() {
        return Stream.of("", "BANK_SERVICE", "BUSINESS_OPERATIONS", "SUPPORT", "unknown", "support", " ")
                .flatMap(actor -> Arrays.stream(UserStatus.values()).flatMap(state -> Stream.of(
                        Arguments.of(actor, state, false), Arguments.of(actor, state, true))));
    }

    @ParameterizedTest
    @MethodSource("queryViews")
    @DisplayName("Ambas consultas aplican la vista por origen y elegibilidad para todos los estados")
    void shouldProjectActualSerializedFields(String actor, UserStatus state, boolean byDocument) throws Exception {
        var entity = UserEntity.builder().id("user-1").documentType(DocumentType.DNI).documentNumber("30111222")
                .firstName("Ana").lastName("Perez").email("ana@example.com").phone("+5491112345678")
                .address("Street 123").birthDate(LocalDate.of(1990, 5, 20)).preferredName("Ani")
                .preferredLanguage("es").status(state).createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-02T00:00:00Z")).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(entity));
        when(userRepository.findByDocumentTypeAndDocumentNumber(DocumentType.DNI, "30111222"))
                .thenReturn(Optional.of(entity));
        var request = byDocument
                ? get("/api/v1/users").param("documentType", "DNI").param("documentNumber", " 30111222 ")
                : get("/api/v1/users/user-1");
        if (!actor.isEmpty()) {
            request.header("X-Query-Actor", actor);
        }
        request.header("X-Actor-Id", "BUSINESS_OPERATIONS");
        var result = mockMvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-1"))
                .andExpect(jsonPath("$.documentType").value("DNI"))
                .andExpect(jsonPath("$.documentNumber").value("30111222"))
                .andExpect(jsonPath("$.status").value(state.name()))
                .andExpect(jsonPath("$.channelLinkEligible").value(state == UserStatus.ACTIVE))
                .andReturn();
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        var names = new java.util.HashSet<String>();
        body.fieldNames().forEachRemaining(names::add);
        var basic = List.of("id", "documentType", "documentNumber", "status", "channelLinkEligible");
        if (actor.equals("BUSINESS_OPERATIONS") || actor.equals("SUPPORT")) {
            assertThat(names).containsAll(basic).hasSize(15);
            assertThat(body.get("email").asText()).isEqualTo("ana@example.com");
            assertThat(body.get("address").asText()).isEqualTo("Street 123");
            assertThat(body.get("birthDate").asText()).isEqualTo("1990-05-20");
        } else {
            assertThat(names).containsExactlyInAnyOrderElementsOf(basic);
            assertThat(result.getResponse().getContentAsString()).doesNotContain("ana@example.com", "Street 123");
        }
        verifyNoInteractions(userAuditService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users/missing", "/api/v1/users?documentType=DNI&documentNumber=123"})
    @DisplayName("Cliente inexistente devuelve 404 sin elegibilidad ni dataset")
    void shouldRejectMissingUser(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.channelLinkEligible").doesNotExist())
                .andExpect(jsonPath("$.details").isArray());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?documentType=DNI", "?documentNumber=123",
            "?documentType=INVALID&documentNumber=123", "?documentType=&documentNumber=123",
            "?documentType=DNI&documentNumber="})
    @DisplayName("Consultas incompletas o inválidas devuelven BAD_REQUEST")
    void shouldRejectInvalidQuery(String query) throws Exception {
        mockMvc.perform(get("/api/v1/users" + query)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verifyNoInteractions(userRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "  ", "\t"})
    @DisplayName("Documento compuesto únicamente por espacios devuelve BAD_REQUEST")
    void shouldRejectBlankDocumentNumber(String number) throws Exception {
        mockMvc.perform(get("/api/v1/users").param("documentType", "DNI").param("documentNumber", number))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verifyNoInteractions(userRepository);
    }
}
