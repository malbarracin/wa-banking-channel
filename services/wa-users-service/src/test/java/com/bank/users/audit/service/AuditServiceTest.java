package com.bank.users.audit.service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bank.users.audit.entity.AuditAction;
import com.bank.users.audit.entity.AuditLog;
import com.bank.users.audit.repository.AuditLogRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository repository;

    @InjectMocks
    private AuditService service;

    @Test
    @DisplayName("Las tres acciones registran fecha, referencia y detalle sin valores personales")
    void recordsSuccessfulOperationsWithSafeDetails() {
        UUID userId = UUID.randomUUID();
        Instant before = Instant.now();

        service.recordUserCreated(userId);
        service.recordUserUpdated(userId, Set.of("phone", "displayName", "email", "preferences"));
        service.recordStatusChanged(userId, "SUSPENDED");

        ArgumentCaptor<AuditLog> captured = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository, times(3)).save(captured.capture());
        assertThat(captured.getAllValues()).extracting(AuditLog::getAction)
                .containsExactly(AuditAction.USER_CREATED, AuditAction.USER_UPDATED, AuditAction.STATUS_CHANGED);
        assertThat(captured.getAllValues()).extracting(AuditLog::getDetail)
                .containsExactly("status=ACTIVE", "fields=displayName,email,phone,preferences", "status=SUSPENDED");
        assertThat(captured.getAllValues()).allSatisfy(entry -> {
            assertThat(entry.getUserId()).isEqualTo(userId);
            assertThat(entry.getResult()).isEqualTo("SUCCESS");
            assertThat(entry.getOccurredAt()).isBetween(before, Instant.now());
        });
    }

    @Test
    @DisplayName("Rechaza campos, estados y referencias inválidos sin persistir datos sensibles")
    void rejectsInvalidMetadataBeforeSaving() {
        UUID userId = UUID.randomUUID();

        assertThatIllegalArgumentException().isThrownBy(() -> service.recordUserCreated(null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.recordUserUpdated(userId, Set.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.recordUserUpdated(userId, Set.of("email=private@example.test")));
        assertThatIllegalArgumentException().isThrownBy(() -> service.recordStatusChanged(userId, ""));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.recordStatusChanged(userId, "private@example.test"));

        verifyNoInteractions(repository);
    }
}
