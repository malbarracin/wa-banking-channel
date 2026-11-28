package com.bcra.users.ws.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.bcra.users.ws.audit.entity.AuditAction;
import com.bcra.users.ws.audit.entity.AuditEntry;
import com.bcra.users.ws.audit.entity.AuditResult;
import com.bcra.users.ws.audit.repository.AuditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class AuditRepositoryTest {
    @Autowired private AuditRepository repository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void returnsOnlyUsersEntriesInChronologicalOrder() {
        UUID id = UUID.randomUUID();
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        jdbc.update("INSERT INTO users (id, external_id, uniqueness_field, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", id, "external", "unique", "ACTIVE", time, time);
        repository.save(new AuditEntry(id, AuditAction.ALTA, AuditResult.OK, null, null, null, "actor", time.plusSeconds(1)));
        repository.save(new AuditEntry(id, AuditAction.ALTA, AuditResult.OK, null, null, null, "actor", time));
        repository.flush();
        assertThat(repository.findByUserIdOrderByOccurredAtAsc(id)).extracting(AuditEntry::getOccurredAt)
                .containsExactly(time, time.plusSeconds(1));
        assertThat(repository.findByUserIdOrderByOccurredAtAsc(UUID.randomUUID())).isEmpty();
    }
}
