package com.bcra.users.ws.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.bcra.users.ws.audit.entity.AuditAction;
import com.bcra.users.ws.audit.entity.AuditEntry;
import com.bcra.users.ws.audit.entity.AuditResult;
import com.bcra.users.ws.audit.repository.AuditRepository;
import com.bcra.users.ws.audit.service.AuditService;
import com.bcra.users.ws.user.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AuditServiceTest {
    @Test
    void recordsActionsWithoutPersonalValues() {
        AuditRepository repository = Mockito.mock(AuditRepository.class);
        AuditService service = new AuditService(repository);
        UUID userId = UUID.randomUUID();
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        service.registerCreation(userId, "actor", AuditResult.OK, time);
        service.registerModification(userId, "actor", AuditResult.OK, time, Set.of("displayName", "contactEmail"));
        service.registerStatusChange(userId, "actor", AuditResult.ERROR, time, UserStatus.ACTIVE, UserStatus.SUSPENDED);
        ArgumentCaptor<AuditEntry> entries = ArgumentCaptor.forClass(AuditEntry.class);
        verify(repository, Mockito.times(3)).save(entries.capture());
        assertThat(entries.getAllValues()).allSatisfy(entry -> {
            assertThat(entry.getUserId()).isEqualTo(userId);
            assertThat(entry.getActor()).isEqualTo("actor");
            assertThat(entry.getOccurredAt()).isEqualTo(time);
            assertThat(entry.getResult()).isNotNull();
        });
        assertThat(entries.getAllValues()).extracting(AuditEntry::getAction)
                .containsExactly(AuditAction.ALTA, AuditAction.MODIFICACION, AuditAction.CAMBIO_ESTADO);
        assertThat(entries.getAllValues().get(1).getChangedFields()).isEqualTo("contactEmail,displayName");
        assertThat(entries.getAllValues().get(2).getPreviousStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(entries.getAllValues().get(2).getNewStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThatThrownBy(() -> service.registerModification(userId, "actor", AuditResult.OK, time,
                Set.of("person@example.com"))).isInstanceOf(IllegalArgumentException.class);
    }
}
