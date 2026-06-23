package com.wa.banking.channel.service;

import com.wa.banking.channel.entity.AuditAction;
import com.wa.banking.channel.entity.InteractionType;
import com.wa.banking.channel.entity.LinkAuditEntryEntity;
import com.wa.banking.channel.entity.InteractionHistoryEntity;
import com.wa.banking.channel.repository.InteractionHistoryRepository;
import com.wa.banking.channel.repository.LinkAuditEntryRepository;
import com.wa.banking.channel.support.LinkTestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private LinkAuditEntryRepository auditRepository;

    @Mock
    private InteractionHistoryRepository interactionHistoryRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    @DisplayName("Should persist audit entry")
    void shouldRecordAudit_whenCalled() {
        auditService.recordAudit(LinkTestFixtures.LINK_ID, AuditAction.LINKED, "SUCCESS", "Linked");

        ArgumentCaptor<LinkAuditEntryEntity> captor = ArgumentCaptor.forClass(LinkAuditEntryEntity.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue().getLinkId()).isEqualTo(LinkTestFixtures.LINK_ID);
        assertThat(captor.getValue().getAction()).isEqualTo(AuditAction.LINKED);
    }

    @Test
    @DisplayName("Should persist interaction history entry")
    void shouldRecordInteraction_whenCalled() {
        auditService.recordInteraction(LinkTestFixtures.LINK_ID, InteractionType.BLOCKED, "SUCCESS", "Blocked");

        ArgumentCaptor<InteractionHistoryEntity> captor = ArgumentCaptor.forClass(InteractionHistoryEntity.class);
        verify(interactionHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(InteractionType.BLOCKED);
    }
}
