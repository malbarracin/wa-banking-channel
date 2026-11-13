package com.bcra.ws.users.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bcra.ws.users.api.v1.mapper.UserMapper;
import com.bcra.ws.users.domain.AuditAction;
import com.bcra.ws.users.domain.AuditResult;
import com.bcra.ws.users.domain.UserStatus;
import com.bcra.ws.users.entity.UserAuditEventEntity;
import com.bcra.ws.users.entity.UserEntity;
import com.bcra.ws.users.exception.GlobalExceptionHandler;
import com.bcra.ws.users.repository.UserAuditEventRepository;
import com.bcra.ws.users.repository.UserRepository;
import com.bcra.ws.users.service.UserAuditService;
import com.bcra.ws.users.service.UserServiceImpl;

@ExtendWith(MockitoExtension.class)
class UserStatusContractTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserAuditEventRepository auditRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void configureRealServicesAndMapper() {
        var service = new UserServiceImpl(userRepository, Mappers.getMapper(UserMapper.class),
                new UserAuditService(auditRepository));
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE,SUSPENDED,true", "ACTIVE,LOGICALLY_DELETED,true", "SUSPENDED,ACTIVE,true",
            "SUSPENDED,LOGICALLY_DELETED,true", "ACTIVE,ACTIVE,false", "SUSPENDED,SUSPENDED,false",
            "LOGICALLY_DELETED,ACTIVE,false", "LOGICALLY_DELETED,SUSPENDED,false",
            "LOGICALLY_DELETED,LOGICALLY_DELETED,false"})
    @DisplayName("La matriz completa valida HTTP, persistencia y auditoría con fecha y actor")
    void shouldEnforceEveryTransition(UserStatus from, UserStatus to, boolean allowed) throws Exception {
        Instant originalTime = Instant.parse("2026-01-01T00:00:00Z");
        UserEntity original = UserEntity.builder().id("user-1").status(from).updatedAt(originalTime).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(original));
        when(auditRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        if (allowed) {
            when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        }
        Instant before = Instant.now();
        var result = mockMvc.perform(patch("/api/v1/users/user-1/status")
                .contentType(MediaType.APPLICATION_JSON).header("X-Actor-Id", "operator")
                .content("{\"status\":\"" + to + "\"}"));
        if (allowed) {
            result.andExpect(status().isOk()).andExpect(jsonPath("$.status").value(to.name()));
            verify(userRepository).save(original);
            assertThat(original.getUpdatedAt()).isAfter(originalTime);
        } else {
            result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"))
                    .andExpect(jsonPath("$.details[0].field").value("status"));
            verify(userRepository, never()).save(any());
            assertThat(original.getStatus()).isEqualTo(from);
            assertThat(original.getUpdatedAt()).isEqualTo(originalTime);
        }
        var captor = ArgumentCaptor.forClass(UserAuditEventEntity.class);
        verify(auditRepository).save(captor.capture());
        UserAuditEventEntity event = captor.getValue();
        assertThat(event.getId()).isNull();
        assertThat(event.getUserId()).isEqualTo("user-1");
        assertThat(event.getAction()).isEqualTo(AuditAction.STATUS_CHANGED);
        assertThat(event.getResult()).isEqualTo(allowed ? AuditResult.SUCCESS : AuditResult.FAILURE);
        assertThat(event.getSummary()).isEqualTo(Map.of("from", from.name(), "to", to.name()));
        assertThat(event.getActor()).isEqualTo("operator");
        assertThat(event.getTimestamp()).isBetween(before, Instant.now());
    }

    @Test
    @DisplayName("GET audit mapea eventos en orden descendente sin escribir el historial")
    void shouldMapAuditWithoutWritingHistory() throws Exception {
        when(userRepository.findById("user-1"))
                .thenReturn(Optional.of(UserEntity.builder().id("user-1").build()));
        Instant latest = Instant.parse("2026-11-01T12:00:00Z");
        var recent = UserAuditEventEntity.builder().id("recent").userId("user-1").timestamp(latest)
                .action(AuditAction.STATUS_CHANGED).result(AuditResult.FAILURE).actor("operator")
                .summary(Map.of("from", "ACTIVE", "to", "ACTIVE")).build();
        var previous = UserAuditEventEntity.builder().id("previous").userId("user-1")
                .timestamp(latest.minusSeconds(1)).action(AuditAction.USER_CREATED)
                .result(AuditResult.SUCCESS).summary(Map.of()).actor("SYSTEM").build();
        when(auditRepository.findByUserIdOrderByTimestampDesc("user-1")).thenReturn(List.of(recent, previous));
        mockMvc.perform(get("/api/v1/users/user-1/audit"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("recent"))
                .andExpect(jsonPath("$[1].id").value("previous"))
                .andExpect(jsonPath("$[0].actor").value("operator"))
                .andExpect(jsonPath("$[0].result").value("FAILURE"))
                .andExpect(jsonPath("$[0].summary.from").value("ACTIVE"));
        verify(auditRepository, never()).save(any());
    }

    @Test
    @DisplayName("GET audit devuelve 404 para usuario ausente")
    void shouldRejectAuditForMissingUser() throws Exception {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/users/missing/audit"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        verify(auditRepository, never()).findByUserIdOrderByTimestampDesc(any());
    }
}
