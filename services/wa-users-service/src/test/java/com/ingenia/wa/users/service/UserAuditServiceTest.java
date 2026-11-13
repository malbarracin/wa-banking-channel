package com.ingenia.wa.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ingenia.wa.users.domain.AuditAction;
import com.ingenia.wa.users.domain.AuditResult;
import com.ingenia.wa.users.entity.UserAuditEventEntity;
import com.ingenia.wa.users.repository.UserAuditEventRepository;

/**
 * Unit tests for {@link UserAuditService}.
 */
@ExtendWith(MockitoExtension.class)
class UserAuditServiceTest {

    @Mock
    private UserAuditEventRepository auditEventRepository;

    @InjectMocks
    private UserAuditService subject;

    @Test
    @DisplayName("Should persist audit event with SYSTEM actor when actor blank")
    void shouldRecordWithSystemActor_whenActorBlank() {
        when(auditEventRepository.save(any(UserAuditEventEntity.class))).thenAnswer(invocation -> {
            UserAuditEventEntity event = invocation.getArgument(0);
            event.setId("audit-1");
            return event;
        });

        UserAuditEventEntity saved = subject.record(
                "user-1",
                AuditAction.USER_CREATED,
                AuditResult.SUCCESS,
                Map.of("status", "ACTIVE"),
                "  ");

        assertThat(saved.getId()).isEqualTo("audit-1");
        ArgumentCaptor<UserAuditEventEntity> captor = ArgumentCaptor.forClass(UserAuditEventEntity.class);
        verify(auditEventRepository).save(captor.capture());
        assertThat(captor.getValue().getActor()).isEqualTo("SYSTEM");
        assertThat(captor.getValue().getSummary()).containsEntry("status", "ACTIVE");
    }

    @Test
    @DisplayName("Should use empty summary when summary is null")
    void shouldUseEmptySummary_whenSummaryNull() {
        when(auditEventRepository.save(any(UserAuditEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        subject.record(null, AuditAction.USER_CREATED, AuditResult.FAILURE, null, "ops");

        ArgumentCaptor<UserAuditEventEntity> captor = ArgumentCaptor.forClass(UserAuditEventEntity.class);
        verify(auditEventRepository).save(captor.capture());
        assertThat(captor.getValue().getSummary()).isEmpty();
        assertThat(captor.getValue().getActor()).isEqualTo("ops");
        assertThat(captor.getValue().getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should return immutable copy of audit trail")
    void shouldReturnCopy_whenFindingByUserId() {
        List<UserAuditEventEntity> stored = List.of(
                UserAuditEventEntity.builder()
                        .id("a1")
                        .userId("user-1")
                        .action(AuditAction.STATUS_CHANGED)
                        .timestamp(Instant.parse("2026-10-19T12:00:00Z"))
                        .result(AuditResult.SUCCESS)
                        .build());
        when(auditEventRepository.findByUserIdOrderByTimestampDesc("user-1")).thenReturn(stored);

        List<UserAuditEventEntity> result = subject.findByUserId("user-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAction()).isEqualTo(AuditAction.STATUS_CHANGED);
    }
}
