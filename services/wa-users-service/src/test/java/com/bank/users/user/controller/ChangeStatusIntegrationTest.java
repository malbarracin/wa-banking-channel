package com.bank.users.user.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.bank.users.audit.entity.AuditAction;
import com.bank.users.audit.repository.AuditLogRepository;
import com.bank.users.audit.service.AuditService;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ChangeStatusIntegrationTest {

    private static final String CREATE_BODY = """
            {"documentType":"DNI","documentNumber":"12345678","displayName":"Ana",
             "email":"ana@example.com","birthDate":"1990-01-02"}
            """;

    private final MockMvc mockMvc;
    private final UserRepository repository;
    private final AuditLogRepository auditRepository;
    private final ObjectMapper objectMapper;

    @MockitoSpyBean
    private AuditService auditService;

    private UUID userId;

    ChangeStatusIntegrationTest(MockMvc mockMvc, UserRepository repository,
            AuditLogRepository auditRepository, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.repository = repository;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void createUser() throws Exception {
        auditRepository.deleteAll();
        repository.deleteAll();
        String response = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        userId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        auditRepository.deleteAll();
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE,SUSPENDED", "ACTIVE,LOGICALLY_DELETED",
        "SUSPENDED,ACTIVE", "SUSPENDED,LOGICALLY_DELETED"})
    @DisplayName("Las cuatro transiciones permitidas persisten estado y auditoría y actualizan ambas consultas")
    void appliesAllowedTransitions(UserStatus initial, UserStatus destination) throws Exception {
        setInitialStatus(initial);

        change(destination).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(destination.name()))
                .andExpect(jsonPath("$.channelLinkEligible").value(destination == UserStatus.ACTIVE));

        assertThat(auditRepository.findAll()).singleElement().satisfies(audit -> {
            assertThat(audit.getUserId()).isEqualTo(userId);
            assertThat(audit.getAction()).isEqualTo(AuditAction.STATUS_CHANGED);
            assertThat(audit.getResult()).isEqualTo("SUCCESS");
            assertThat(audit.getDetail()).isEqualTo("status=" + destination.name());
            assertThat(audit.getOccurredAt()).isNotNull();
        });
        assertQueries(destination);
        assertThat(repository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE,ACTIVE", "SUSPENDED,SUSPENDED", "LOGICALLY_DELETED,ACTIVE",
        "LOGICALLY_DELETED,SUSPENDED", "LOGICALLY_DELETED,LOGICALLY_DELETED"})
    @DisplayName("Las otras cinco transiciones devuelven conflicto sin cambios ni auditoría")
    void rejectsForbiddenTransitions(UserStatus initial, UserStatus destination) throws Exception {
        setInitialStatus(initial);
        Instant updatedAt = repository.findById(userId).orElseThrow().getUpdatedAt();

        assertError(change(destination), 409, "CONFLICT");

        assertThat(auditRepository.count()).isZero();
        assertThat(repository.findById(userId).orElseThrow().getUpdatedAt()).isEqualTo(updatedAt);
        assertQueries(initial);
        assertThat(repository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "{}|VALIDATION_ERROR", "{\"status\":null}|VALIDATION_ERROR",
        "{\"status\":\"UNKNOWN\"}|BAD_REQUEST", "{\"status\":\"active\"}|BAD_REQUEST",
        "{\"status\":{}}|BAD_REQUEST", "null|BAD_REQUEST"
    }, delimiter = '|')
    @DisplayName("Estado ausente o inválido respeta el contrato común sin persistir cambios")
    void rejectsInvalidStatus(String body, String code) throws Exception {
        assertError(mockMvc.perform(patch("/api/v1/users/{id}/status", userId)
                .contentType(MediaType.APPLICATION_JSON).content(body)), 400, code);
        assertQueries(UserStatus.ACTIVE);
        assertThat(auditRepository.count()).isZero();
    }

    @Test
    @DisplayName("Un identificador inexistente devuelve NOT_FOUND y uno mal formado BAD_REQUEST")
    void rejectsInvalidIdentifiers() throws Exception {
        for (String id : List.of(UUID.randomUUID().toString(), "invalid")) {
            boolean missing = !"invalid".equals(id);
            assertError(mockMvc.perform(patch("/api/v1/users/{id}/status", id)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}")),
                    missing ? 404 : 400, missing ? "NOT_FOUND" : "BAD_REQUEST");
        }
        assertThat(auditRepository.count()).isZero();
    }

    @Test
    @DisplayName("El flujo H1 audita alta, update y estados; la baja conserva documento e impide modificaciones")
    void completesLifecycle() throws Exception {
        repository.deleteAll();
        String response = mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        userId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        assertQueries(UserStatus.ACTIVE);
        mockMvc.perform(patch("/api/v1/users/{id}", userId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Nuevo nombre\"}"))
                .andExpect(status().isOk());
        for (UserStatus destination : List.of(UserStatus.SUSPENDED, UserStatus.ACTIVE,
                UserStatus.LOGICALLY_DELETED)) {
            change(destination).andExpect(status().isOk());
            assertQueries(destination);
        }
        assertError(change(UserStatus.ACTIVE), 409, "CONFLICT");
        assertError(mockMvc.perform(patch("/api/v1/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Otro\"}")), 409, "CONFLICT");
        assertError(mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY)), 409, "CONFLICT");
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById(userId).orElseThrow().getDisplayName()).isEqualTo("Nuevo nombre");
        assertThat(auditRepository.findAll()).extracting(audit -> audit.getAction())
                .containsExactlyInAnyOrder(AuditAction.USER_CREATED, AuditAction.USER_UPDATED,
                        AuditAction.STATUS_CHANGED, AuditAction.STATUS_CHANGED, AuditAction.STATUS_CHANGED);
    }

    @Test
    @DisplayName("Un fallo tras insertar auditoría revierte tanto el estado enviado a BD como la auditoría")
    void rollsBackStatusAndAudit() throws Exception {
        AuditService auditTarget = AopTestUtils.getUltimateTargetObject(auditService);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            auditRepository.flush();
            throw new IllegalStateException("audit unavailable");
        }).when(auditTarget).recordStatusChanged(any(), any());

        assertError(change(UserStatus.SUSPENDED), 500, "INTERNAL_ERROR");

        assertQueries(UserStatus.ACTIVE);
        assertThat(auditRepository.count()).isZero();
    }

    private ResultActions change(UserStatus destination) throws Exception {
        return mockMvc.perform(patch("/api/v1/users/{id}/status", userId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + destination.name() + "\"}"));
    }

    private void setInitialStatus(UserStatus initial) {
        User user = repository.findById(userId).orElseThrow();
        user.setStatus(initial);
        repository.saveAndFlush(user);
    }

    private void assertQueries(UserStatus expected) throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(expected.name()))
                .andExpect(jsonPath("$.channelLinkEligible").value(expected == UserStatus.ACTIVE));
        mockMvc.perform(get("/api/v1/users").param("documentType", "DNI").param("documentNumber", "12345678"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(expected.name()))
                .andExpect(jsonPath("$.channelLinkEligible").value(expected == UserStatus.ACTIVE));
    }

    private void assertError(ResultActions response, int httpStatus, String code) throws Exception {
        response.andExpect(status().is(httpStatus)).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isNotEmpty()).andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.stackTrace").doesNotExist());
    }
}
