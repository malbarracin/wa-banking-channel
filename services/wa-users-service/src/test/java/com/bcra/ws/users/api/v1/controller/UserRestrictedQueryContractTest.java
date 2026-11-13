package com.bcra.ws.users.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

@WebMvcTest(value = UserController.class,
        properties = "users.query.full-view-actors=BUSINESS_OPERATIONS,BANK_SERVICE,unknown")
@Import({UserServiceImpl.class, UserMapperImpl.class, UserQueryProjection.class})
class UserRestrictedQueryContractTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private UserAuditService userAuditService;

    static Stream<Arguments> restrictedQueries() {
        return Stream.of("SUPPORT", "BANK_SERVICE", "unknown")
                .flatMap(actor -> Stream.of(Arguments.of(actor, false), Arguments.of(actor, true)));
    }

    @ParameterizedTest
    @MethodSource("restrictedQueries")
    @DisplayName("La configuración restringe soporte en HTTP y no amplía servicios ni actores desconocidos")
    void shouldApplyConfiguredRestrictionToBothQueries(String actor, boolean byDocument) throws Exception {
        var user = UserEntity.builder().id("restricted-user").documentType(DocumentType.DNI)
                .documentNumber("30111222").email("private@example.com").status(UserStatus.ACTIVE).build();
        when(userRepository.findById("restricted-user")).thenReturn(Optional.of(user));
        when(userRepository.findByDocumentTypeAndDocumentNumber(DocumentType.DNI, "30111222"))
                .thenReturn(Optional.of(user));
        var request = byDocument
                ? get("/api/v1/users").param("documentType", "DNI").param("documentNumber", "30111222")
                : get("/api/v1/users/restricted-user");
        var result = mockMvc.perform(request.header("X-Query-Actor", actor)
                        .header("X-Actor-Id", "BUSINESS_OPERATIONS"))
                .andExpect(status().isOk()).andReturn();
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        var fields = new HashSet<String>();
        body.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "documentType", "documentNumber", "status",
                "channelLinkEligible");
        assertThat(body.path("id").asText()).isEqualTo("restricted-user");
        assertThat(body.path("channelLinkEligible").asBoolean()).isTrue();
    }
}
